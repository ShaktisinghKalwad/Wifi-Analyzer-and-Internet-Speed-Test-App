package com.srk.wifianalyzer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.content.Context
import android.content.Intent
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.srk.wifianalyzer.MainActivity
import com.srk.wifianalyzer.R
import com.srk.wifianalyzer.domain.model.WifiScanState
import com.srk.wifianalyzer.domain.repository.WifiRepository
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import com.srk.wifianalyzer.settings.domain.models.UserSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WifiScanForegroundService : LifecycleService() {

    @Inject
    lateinit var wifiRepository: WifiRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var wifiManager: WifiManager

    @Inject
    lateinit var connectivityManager: ConnectivityManager

    private var scanJob: Job? = null
    private var settingsJob: Job? = null

    private var currentSettings: UserSettings = UserSettings()

    private var lastConnectedWifi: ConnectedWifi? = null
    private var wasWeakSignal: Boolean = false
    private var lastWeakSignalNotificationAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels()

        if (!canPostNotifications()) {
            stopSelf()
            return
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(null))
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to start foreground notification", throwable)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> startScanning()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scanJob?.cancel()
        scanJob = null
        settingsJob?.cancel()
        settingsJob = null
        super.onDestroy()
    }

    private fun startScanning() {
        if (scanJob != null) return

        settingsJob = (settingsJob ?: lifecycleScope.launch {
            settingsRepository.userSettings.collectLatest { settings ->
                currentSettings = settings
            }
        })

        scanJob = lifecycleScope.launch {
            wifiRepository.observeWifiScanState().collectLatest { state ->
                val nm = getSystemService<NotificationManager>() ?: return@collectLatest
                nm.notify(NOTIFICATION_ID, buildNotification(state))

                val connected = getConnectedWifi()
                evaluateAlerts(connected, currentSettings)
            }
        }
    }

    private fun evaluateAlerts(connectedWifi: ConnectedWifi?, settings: UserSettings) {
        if (!canPostNotifications()) {
            lastConnectedWifi = connectedWifi
            wasWeakSignal = false
            return
        }

        if (connectedWifi == null) {
            if (settings.alerts.wifiDisconnectNotificationEnabled && lastConnectedWifi != null) {
                NotificationManagerCompat.from(this).notify(
                    DISCONNECT_NOTIFICATION_ID,
                    buildAlertNotification(
                        title = "Wi-Fi disconnected",
                        content = "Wi-Fi connection lost",
                    ),
                )
            }

            lastConnectedWifi = null
            wasWeakSignal = false
            return
        }

        val previous = lastConnectedWifi
        if (
            settings.alerts.networkChangeDetectionEnabled &&
            previous != null &&
            (previous.ssid != connectedWifi.ssid || previous.bssid != connectedWifi.bssid)
        ) {
            val from = previous.ssid.ifBlank { "(Hidden)" }
            val to = connectedWifi.ssid.ifBlank { "(Hidden)" }
            NotificationManagerCompat.from(this).notify(
                NETWORK_CHANGE_NOTIFICATION_ID,
                buildAlertNotification(
                    title = "Wi-Fi network changed",
                    content = "$from -> $to",
                ),
            )
        }

        lastConnectedWifi = connectedWifi

        val rssiDbm = connectedWifi.rssiDbm
        if (rssiDbm != null) {
            val threshold = settings.alerts.weakSignalThresholdDbm
            val enterWeak = rssiDbm <= threshold
            val exitWeak = rssiDbm > (threshold + WEAK_SIGNAL_HYSTERESIS_DB)

            if (!wasWeakSignal && enterWeak) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastWeakSignalNotificationAtMs >= WEAK_SIGNAL_COOLDOWN_MS) {
                    lastWeakSignalNotificationAtMs = now
                    NotificationManagerCompat.from(this).notify(
                        WEAK_SIGNAL_NOTIFICATION_ID,
                        buildAlertNotification(
                            title = "Weak Wi-Fi signal",
                            content = "${connectedWifi.ssid.ifBlank { "(Hidden)" }} -> ${rssiDbm} dBm",
                        ),
                    )
                }
                wasWeakSignal = true
            } else if (wasWeakSignal && exitWeak) {
                wasWeakSignal = false
            }
        }
    }

    private fun buildAlertNotification(title: String, content: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            11,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )

        return NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun buildNotification(state: WifiScanState?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WifiScanForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val apCount = state?.accessPoints?.size
        val strongest = state?.accessPoints?.maxByOrNull { it.rssiDbm }

        val contentText = when {
            apCount == null -> "Starting…"
            apCount == 0 -> "Scanning…"
            strongest != null -> "${apCount} APs • ${strongest.ssid.ifBlank { "(Hidden)" }} ${strongest.rssiDbm} dBm"
            else -> "${apCount} APs"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Wi‑Fi scanning")
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return

        val nm = getSystemService<NotificationManager>() ?: return

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi scanning",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        if (nm.getNotificationChannel(ALERTS_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ALERTS_CHANNEL_ID,
                    "Wi-Fi alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    private fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }

        return true
    }

    private fun getConnectedWifi(): ConnectedWifi? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        return try {
            val info = wifiManager.connectionInfo ?: return null

            val rawSsid = info.ssid ?: ""
            val ssid = rawSsid.trim().trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: ""
            val bssid = info.bssid?.takeIf { it.isNotBlank() && it != "00:00:00:00:00:00" }
            val rssi = info.rssi.takeIf { it < 0 }

            if (info.networkId == -1) return null

            ConnectedWifi(
                ssid = ssid,
                bssid = bssid,
                rssiDbm = rssi,
            )
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private data class ConnectedWifi(
        val ssid: String,
        val bssid: String?,
        val rssiDbm: Int?,
    )

    companion object {
        private const val TAG = "WifiScanForegroundService"
        private const val CHANNEL_ID = "wifi_scan"
        private const val NOTIFICATION_ID = 1001

        private const val ALERTS_CHANNEL_ID = "wifi_alerts"

        private const val WEAK_SIGNAL_NOTIFICATION_ID = 2001
        private const val DISCONNECT_NOTIFICATION_ID = 2002
        private const val NETWORK_CHANGE_NOTIFICATION_ID = 2003

        private const val WEAK_SIGNAL_COOLDOWN_MS = 2 * 60 * 1000L
        private const val WEAK_SIGNAL_HYSTERESIS_DB = 3

        const val ACTION_STOP = "com.srk.wifianalyzer.action.STOP_SCAN"

        fun start(context: Context) {
            val intent = Intent(context, WifiScanForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to start scan foreground service", throwable)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WifiScanForegroundService::class.java))
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to request scan foreground service stop", throwable)
            }
        }
    }
}
