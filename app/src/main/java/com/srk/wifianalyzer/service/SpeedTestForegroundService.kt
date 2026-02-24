package com.srk.wifianalyzer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.srk.wifianalyzer.MainActivity
import com.srk.wifianalyzer.R
import com.srk.wifianalyzer.data.speedtest.SpeedTestConfig
import com.srk.wifianalyzer.data.speedtest.SpeedTestEngine
import com.srk.wifianalyzer.data.speedtest.SpeedTestStage
import com.srk.wifianalyzer.settings.domain.models.SpeedTestMode
import com.srk.wifianalyzer.settings.domain.models.SpeedUnit
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SpeedTestForegroundService : LifecycleService() {

    @Inject
    lateinit var speedTestEngine: SpeedTestEngine

    private var collectJob: Job? = null

    private var speedUnit: SpeedUnit = SpeedUnit.MBps

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to start foreground notification", throwable)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                speedTestEngine.stop()
                stopSelf()
            }

            else -> {
                val mode = (intent?.getStringExtra(EXTRA_MODE) ?: SpeedTestMode.Full.name)
                    .let { runCatching { SpeedTestMode.valueOf(it) }.getOrDefault(SpeedTestMode.Full) }
                speedUnit = (intent?.getStringExtra(EXTRA_SPEED_UNIT) ?: SpeedUnit.MBps.name)
                    .let { runCatching { SpeedUnit.valueOf(it) }.getOrDefault(SpeedUnit.MBps) }

                val downloadUrl = intent?.getStringExtra(EXTRA_DOWNLOAD_URL).orEmpty().trim()
                val uploadUrl = intent?.getStringExtra(EXTRA_UPLOAD_URL).orEmpty().trim()

                val base = SpeedTestConfig().let { cfg ->
                    when {
                        downloadUrl.isNotBlank() && uploadUrl.isNotBlank() -> cfg.copy(downloadUrl = downloadUrl, uploadUrl = uploadUrl)
                        downloadUrl.isNotBlank() -> cfg.copy(downloadUrl = downloadUrl)
                        uploadUrl.isNotBlank() -> cfg.copy(uploadUrl = uploadUrl)
                        else -> cfg
                    }
                }
                val config = when (mode) {
                    SpeedTestMode.Full -> base
                    SpeedTestMode.DownloadOnly -> base.copy(uploadDurationMs = 0L, intermissionMs = 0L)
                    SpeedTestMode.UploadOnly -> base.copy(downloadDurationMs = 0L, intermissionMs = 0L)
                }

                speedTestEngine.startFullTest(config)
                startCollecting()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        super.onDestroy()
    }

    private fun startCollecting() {
        if (collectJob != null) return

        collectJob = lifecycleScope.launch {
            speedTestEngine.state.collectLatest { state ->
                val nm = getSystemService<NotificationManager>() ?: return@collectLatest
                nm.notify(NOTIFICATION_ID, buildNotification())

                if (state.stage == SpeedTestStage.Completed || state.stage == SpeedTestStage.Error) {
                    delay(800)
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val state = speedTestEngine.state.value

        val multiplier = if (speedUnit == SpeedUnit.Mbps) 8.0 else 1.0

        val contentIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )

        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, SpeedTestForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )

        val progressPct = (state.totalProgress.coerceIn(0f, 1f) * 100f)
        val pct = fmt0(progressPct)

        val contentText = when (state.stage) {
            SpeedTestStage.Idle -> getString(R.string.stage_ready)
            SpeedTestStage.Countdown -> getString(R.string.stage_starting_in, state.countdownRemainingSec)
            SpeedTestStage.Downloading -> getString(
                if (speedUnit == SpeedUnit.Mbps) R.string.speed_test_notification_download_mbps else R.string.speed_test_notification_download,
                fmt(state.download.currentMBps * multiplier),
                pct,
            )
            SpeedTestStage.PreparingUpload -> getString(
                R.string.speed_test_notification_preparing_upload,
                pct,
            )
            SpeedTestStage.Uploading -> getString(
                if (speedUnit == SpeedUnit.Mbps) R.string.speed_test_notification_upload_mbps else R.string.speed_test_notification_upload,
                fmt(state.upload.currentMBps * multiplier),
                pct,
            )
            SpeedTestStage.Completed -> getString(
                if (speedUnit == SpeedUnit.Mbps) R.string.speed_test_notification_done_mbps else R.string.speed_test_notification_done,
                fmt(state.download.avgMBps * multiplier),
                fmt(state.upload.avgMBps * multiplier),
            )
            SpeedTestStage.Error -> getString(R.string.stage_error)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.speed_test_notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(
                state.stage == SpeedTestStage.Countdown ||
                    state.stage == SpeedTestStage.Downloading ||
                    state.stage == SpeedTestStage.PreparingUpload ||
                    state.stage == SpeedTestStage.Uploading
            )
            .addAction(0, getString(R.string.stop), stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val nm = getSystemService<NotificationManager>() ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.speed_test_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun fmt(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun fmt0(value: Float): String {
        return String.format(Locale.US, "%.0f", value)
    }

    companion object {
        private const val TAG = "SpeedTestForegroundService"
        private const val CHANNEL_ID = "speed_test"
        private const val NOTIFICATION_ID = 2001

        private const val EXTRA_MODE = "extra_speed_test_mode"
        private const val EXTRA_SPEED_UNIT = "extra_speed_test_unit"
        private const val EXTRA_DOWNLOAD_URL = "extra_speed_test_download_url"
        private const val EXTRA_UPLOAD_URL = "extra_speed_test_upload_url"

        const val ACTION_STOP = "com.srk.wifianalyzer.action.STOP_SPEED_TEST"

        fun start(
            context: Context,
            mode: SpeedTestMode = SpeedTestMode.Full,
            unit: SpeedUnit = SpeedUnit.MBps,
            downloadUrl: String? = null,
            uploadUrl: String? = null,
        ) {
            val intent = Intent(context, SpeedTestForegroundService::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_SPEED_UNIT, unit.name)
                if (!downloadUrl.isNullOrBlank()) putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                if (!uploadUrl.isNullOrBlank()) putExtra(EXTRA_UPLOAD_URL, uploadUrl)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpeedTestForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
                runCatching {
                    context.stopService(Intent(context, SpeedTestForegroundService::class.java))
                }
            }
        }
    }
}
