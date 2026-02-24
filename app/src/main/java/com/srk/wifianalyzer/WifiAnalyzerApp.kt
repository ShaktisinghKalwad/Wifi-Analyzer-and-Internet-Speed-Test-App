package com.srk.wifianalyzer

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.srk.wifianalyzer.service.WifiScanForegroundService
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class WifiAnalyzerApp : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var appScope: CoroutineScope

    @Volatile
    private var startedActivities: Int = 0

    @Volatile
    private var backgroundAutoScanEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}

                override fun onActivityStarted(activity: Activity) {
                    startedActivities += 1
                    applyBackgroundAutoScan()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                    applyBackgroundAutoScan()
                }
            },
        )

        appScope.launch(
            CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Auto-scan watcher crashed", throwable)
            }
        ) {
            settingsRepository.userSettings
                .map { it.alerts.backgroundAutoScanEnabled }
                .distinctUntilChanged()
                .catch { throwable ->
                    Log.e(TAG, "Failed to observe background auto-scan setting", throwable)
                    emit(false)
                }
                .collect { enabled ->
                    backgroundAutoScanEnabled = enabled
                    applyBackgroundAutoScan()
                }
        }
    }

    private fun isAppInForeground(): Boolean {
        return startedActivities > 0
    }

    private fun applyBackgroundAutoScan() {
        val canRun = backgroundAutoScanEnabled && canStartForegroundScanService()
        val canAttemptStartNow = canRun && isAppInForeground()
        try {
            if (canAttemptStartNow) {
                WifiScanForegroundService.start(this)
            } else if (!canRun) {
                WifiScanForegroundService.stop(this)
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to apply background auto-scan (enabled=$backgroundAutoScanEnabled)", throwable)
            runCatching { WifiScanForegroundService.stop(this) }
        }
    }

    private fun canPostServiceNotification(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun canStartForegroundScanService(): Boolean {
        return canPostServiceNotification() && hasLocationPermission()
    }

    companion object {
        private const val TAG = "WifiAnalyzerApp"
    }
}
