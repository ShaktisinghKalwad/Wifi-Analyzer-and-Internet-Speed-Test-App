package com.srk.wifianalyzer.settings.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import com.srk.wifianalyzer.settings.domain.models.AlertsAutomationSettings
import com.srk.wifianalyzer.settings.domain.models.DeveloperSettings
import com.srk.wifianalyzer.settings.domain.models.ExportFormat
import com.srk.wifianalyzer.settings.domain.models.HistoryLogsSettings
import com.srk.wifianalyzer.settings.domain.models.HistoryRetention
import com.srk.wifianalyzer.settings.domain.models.PerformanceBatterySettings
import com.srk.wifianalyzer.settings.domain.models.PrivacySecuritySettings
import com.srk.wifianalyzer.settings.domain.models.ScanInterval
import com.srk.wifianalyzer.settings.domain.models.SettingsTheme
import com.srk.wifianalyzer.settings.domain.models.SignalUnit
import com.srk.wifianalyzer.settings.domain.models.SpeedTestGraphStyle
import com.srk.wifianalyzer.settings.domain.models.SpeedTestMode
import com.srk.wifianalyzer.settings.domain.models.SpeedTestServerSelection
import com.srk.wifianalyzer.settings.domain.models.SpeedTestSettings
import com.srk.wifianalyzer.settings.domain.models.SpeedUnit
import com.srk.wifianalyzer.settings.domain.models.ThreadPriority
import com.srk.wifianalyzer.settings.domain.models.UiUxSettings
import com.srk.wifianalyzer.settings.domain.models.UserSettings
import com.srk.wifianalyzer.settings.domain.models.WifiScanSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : SettingsRepository {

    override val userSettings: Flow<UserSettings> = settingsDataStore.dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs -> prefs.toUserSettings() }

    override suspend fun updateUserSettings(reducer: (UserSettings) -> UserSettings) {
        settingsDataStore.dataStore.edit { prefs ->
            val current = prefs.toUserSettings()
            val next = reducer(current).copy(schemaVersion = UserSettings.CURRENT_SCHEMA_VERSION)
            prefs.applyUserSettings(next)
        }
    }

    override suspend fun resetToDefaults() {
        settingsDataStore.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val version = this[Keys.schemaVersion] ?: 0

        val wifi = WifiScanSettings(
            scanInterval = (this[Keys.wifiScanIntervalSeconds] ?: WifiScanSettings().scanInterval.seconds)
                .toScanIntervalOrDefault(),
            signalUnit = (this[Keys.wifiSignalUnit] ?: WifiScanSettings().signalUnit.name)
                .toSignalUnitOrDefault(),
            band2gEnabled = this[Keys.wifiBand2gEnabled] ?: WifiScanSettings().band2gEnabled,
            band5gEnabled = this[Keys.wifiBand5gEnabled] ?: WifiScanSettings().band5gEnabled,
            band6gEnabled = this[Keys.wifiBand6gEnabled] ?: WifiScanSettings().band6gEnabled,
            channelOverlapEnabled = this[Keys.wifiChannelOverlapEnabled] ?: WifiScanSettings().channelOverlapEnabled,
            rssiSmoothingEnabled = this[Keys.wifiRssiSmoothingEnabled] ?: WifiScanSettings().rssiSmoothingEnabled,
            rssiSmoothingAlpha = (this[Keys.wifiRssiSmoothingAlpha] ?: WifiScanSettings().rssiSmoothingAlpha)
                .coerceIn(0f, 1f),
            showChannelWidths = (this[Keys.wifiShowChannelWidths] ?: WifiScanSettings().showChannelWidths.map { it.toString() }.toSet())
                .mapNotNull { it.toIntOrNull() }
                .toSet()
                .ifEmpty { WifiScanSettings().showChannelWidths },
        )

        val speedTest = SpeedTestSettings(
            speedUnit = (this[Keys.speedUnit] ?: SpeedTestSettings().speedUnit.name).toSpeedUnitOrDefault(),
            mode = (this[Keys.speedTestMode] ?: SpeedTestSettings().mode.name).toSpeedTestModeOrDefault(),
            serverSelection = (this[Keys.speedTestServerSelection] ?: SpeedTestSettings().serverSelection.name)
                .toServerSelectionOrDefault(),
            manualServerUrl = this[Keys.speedTestManualServerUrl] ?: SpeedTestSettings().manualServerUrl,
            jitterAndPacketLossEnabled = this[Keys.speedTestJitterPacketLossEnabled]
                ?: SpeedTestSettings().jitterAndPacketLossEnabled,
            graphStyle = (this[Keys.speedTestGraphStyle] ?: SpeedTestSettings().graphStyle.name).toGraphStyleOrDefault(),
        )

        val alerts = AlertsAutomationSettings(
            weakSignalThresholdDbm = (this[Keys.alertWeakSignalThresholdDbm] ?: AlertsAutomationSettings().weakSignalThresholdDbm)
                .coerceIn(-100, -30),
            wifiDisconnectNotificationEnabled = this[Keys.alertWifiDisconnectEnabled]
                ?: AlertsAutomationSettings().wifiDisconnectNotificationEnabled,
            networkChangeDetectionEnabled = this[Keys.alertNetworkChangeDetectionEnabled]
                ?: AlertsAutomationSettings().networkChangeDetectionEnabled,
            backgroundAutoScanEnabled = this[Keys.alertBackgroundAutoScanEnabled]
                ?: AlertsAutomationSettings().backgroundAutoScanEnabled,
        )

        val history = HistoryLogsSettings(
            scanHistoryEnabled = this[Keys.historyEnabled] ?: HistoryLogsSettings().scanHistoryEnabled,
            retention = (this[Keys.historyRetention] ?: HistoryLogsSettings().retention.name).toHistoryRetentionOrDefault(),
            exportFormat = (this[Keys.historyExportFormat] ?: HistoryLogsSettings().exportFormat.name).toExportFormatOrDefault(),
            autoClearOldLogs = this[Keys.historyAutoClearOldLogs] ?: HistoryLogsSettings().autoClearOldLogs,
        )

        val ui = UiUxSettings(
            theme = (this[Keys.uiTheme] ?: UiUxSettings().theme.name).toSettingsThemeOrDefault(),
            dynamicColorEnabled = this[Keys.uiDynamicColorEnabled] ?: UiUxSettings().dynamicColorEnabled,
            fontScale = (this[Keys.uiFontScale] ?: UiUxSettings().fontScale).coerceIn(0.85f, 1.3f),
            graphDensity = (this[Keys.uiGraphDensity] ?: UiUxSettings().graphDensity).coerceIn(0.5f, 2.0f),
            proModeEnabled = this[Keys.uiProModeEnabled] ?: UiUxSettings().proModeEnabled,
        )

        val performance = PerformanceBatterySettings(
            batterySaverModeEnabled = this[Keys.perfBatterySaverEnabled] ?: PerformanceBatterySettings().batterySaverModeEnabled,
            adaptiveScanEngineEnabled = this[Keys.perfAdaptiveScanEnabled] ?: PerformanceBatterySettings().adaptiveScanEngineEnabled,
            backgroundScanFrequencyLimitSec = (this[Keys.perfBackgroundScanLimitSec]
                ?: PerformanceBatterySettings().backgroundScanFrequencyLimitSec).coerceIn(15, 3600),
            threadPriority = (this[Keys.perfThreadPriority] ?: PerformanceBatterySettings().threadPriority.name)
                .toThreadPriorityOrDefault(),
        )

        val privacy = PrivacySecuritySettings(
            localOnlyProcessingEnabled = this[Keys.privacyLocalOnlyEnabled] ?: PrivacySecuritySettings().localOnlyProcessingEnabled,
        )

        val developer = DeveloperSettings(
            developerModeEnabled = this[Keys.devModeEnabled] ?: DeveloperSettings().developerModeEnabled,
            showRawScanData = this[Keys.devShowRawScanData] ?: DeveloperSettings().showRawScanData,
            showPhyMode = this[Keys.devShowPhyMode] ?: DeveloperSettings().showPhyMode,
            showLinkSpeed = this[Keys.devShowLinkSpeed] ?: DeveloperSettings().showLinkSpeed,
            experimentalFlags = this[Keys.devExperimentalFlags] ?: DeveloperSettings().experimentalFlags,
        )

        val normalizedVersion = if (version > 0) version else UserSettings.CURRENT_SCHEMA_VERSION

        return UserSettings(
            schemaVersion = normalizedVersion,
            wifi = wifi,
            speedTest = speedTest,
            alerts = alerts,
            history = history,
            ui = ui,
            performance = performance,
            privacy = privacy,
            developer = developer,
        )
    }

    private fun MutablePreferences.applyUserSettings(settings: UserSettings) {
        this[Keys.schemaVersion] = settings.schemaVersion

        this[Keys.wifiScanIntervalSeconds] = settings.wifi.scanInterval.seconds
        this[Keys.wifiSignalUnit] = settings.wifi.signalUnit.name
        this[Keys.wifiBand2gEnabled] = settings.wifi.band2gEnabled
        this[Keys.wifiBand5gEnabled] = settings.wifi.band5gEnabled
        this[Keys.wifiBand6gEnabled] = settings.wifi.band6gEnabled
        this[Keys.wifiChannelOverlapEnabled] = settings.wifi.channelOverlapEnabled
        this[Keys.wifiRssiSmoothingEnabled] = settings.wifi.rssiSmoothingEnabled
        this[Keys.wifiRssiSmoothingAlpha] = settings.wifi.rssiSmoothingAlpha.coerceIn(0f, 1f)
        this[Keys.wifiShowChannelWidths] = settings.wifi.showChannelWidths.map { it.toString() }.toSet()

        this[Keys.speedUnit] = settings.speedTest.speedUnit.name
        this[Keys.speedTestMode] = settings.speedTest.mode.name
        this[Keys.speedTestServerSelection] = settings.speedTest.serverSelection.name
        this[Keys.speedTestManualServerUrl] = settings.speedTest.manualServerUrl
        this[Keys.speedTestJitterPacketLossEnabled] = settings.speedTest.jitterAndPacketLossEnabled
        this[Keys.speedTestGraphStyle] = settings.speedTest.graphStyle.name

        this[Keys.alertWeakSignalThresholdDbm] = settings.alerts.weakSignalThresholdDbm.coerceIn(-100, -30)
        this[Keys.alertWifiDisconnectEnabled] = settings.alerts.wifiDisconnectNotificationEnabled
        this[Keys.alertNetworkChangeDetectionEnabled] = settings.alerts.networkChangeDetectionEnabled
        this[Keys.alertBackgroundAutoScanEnabled] = settings.alerts.backgroundAutoScanEnabled

        this[Keys.historyEnabled] = settings.history.scanHistoryEnabled
        this[Keys.historyRetention] = settings.history.retention.name
        this[Keys.historyExportFormat] = settings.history.exportFormat.name
        this[Keys.historyAutoClearOldLogs] = settings.history.autoClearOldLogs

        this[Keys.uiTheme] = settings.ui.theme.name
        this[Keys.uiDynamicColorEnabled] = settings.ui.dynamicColorEnabled
        this[Keys.uiFontScale] = settings.ui.fontScale.coerceIn(0.85f, 1.3f)
        this[Keys.uiGraphDensity] = settings.ui.graphDensity.coerceIn(0.5f, 2.0f)
        this[Keys.uiProModeEnabled] = settings.ui.proModeEnabled

        this[Keys.perfBatterySaverEnabled] = settings.performance.batterySaverModeEnabled
        this[Keys.perfAdaptiveScanEnabled] = settings.performance.adaptiveScanEngineEnabled
        this[Keys.perfBackgroundScanLimitSec] = settings.performance.backgroundScanFrequencyLimitSec.coerceIn(15, 3600)
        this[Keys.perfThreadPriority] = settings.performance.threadPriority.name

        this[Keys.privacyLocalOnlyEnabled] = settings.privacy.localOnlyProcessingEnabled

        this[Keys.devModeEnabled] = settings.developer.developerModeEnabled
        this[Keys.devShowRawScanData] = settings.developer.showRawScanData
        this[Keys.devShowPhyMode] = settings.developer.showPhyMode
        this[Keys.devShowLinkSpeed] = settings.developer.showLinkSpeed
        this[Keys.devExperimentalFlags] = settings.developer.experimentalFlags
    }

    private object Keys {
        val schemaVersion = intPreferencesKey("schema_version")

        val wifiScanIntervalSeconds = intPreferencesKey("wifi_scan_interval_seconds")
        val wifiSignalUnit = stringPreferencesKey("wifi_signal_unit")
        val wifiBand2gEnabled = booleanPreferencesKey("wifi_band_2g_enabled")
        val wifiBand5gEnabled = booleanPreferencesKey("wifi_band_5g_enabled")
        val wifiBand6gEnabled = booleanPreferencesKey("wifi_band_6g_enabled")
        val wifiChannelOverlapEnabled = booleanPreferencesKey("wifi_channel_overlap_enabled")
        val wifiRssiSmoothingEnabled = booleanPreferencesKey("wifi_rssi_smoothing_enabled")
        val wifiRssiSmoothingAlpha = floatPreferencesKey("wifi_rssi_smoothing_alpha")
        val wifiShowChannelWidths = stringSetPreferencesKey("wifi_show_channel_widths")

        val speedUnit = stringPreferencesKey("speed_unit")
        val speedTestMode = stringPreferencesKey("speed_test_mode")
        val speedTestServerSelection = stringPreferencesKey("speed_test_server_selection")
        val speedTestManualServerUrl = stringPreferencesKey("speed_test_manual_server_url")
        val speedTestJitterPacketLossEnabled = booleanPreferencesKey("speed_test_jitter_packet_loss_enabled")
        val speedTestGraphStyle = stringPreferencesKey("speed_test_graph_style")

        val alertWeakSignalThresholdDbm = intPreferencesKey("alert_weak_signal_threshold_dbm")
        val alertWifiDisconnectEnabled = booleanPreferencesKey("alert_wifi_disconnect_enabled")
        val alertNetworkChangeDetectionEnabled = booleanPreferencesKey("alert_network_change_detection_enabled")
        val alertBackgroundAutoScanEnabled = booleanPreferencesKey("alert_background_auto_scan_enabled")

        val historyEnabled = booleanPreferencesKey("history_enabled")
        val historyRetention = stringPreferencesKey("history_retention")
        val historyExportFormat = stringPreferencesKey("history_export_format")
        val historyAutoClearOldLogs = booleanPreferencesKey("history_auto_clear_old_logs")

        val uiTheme = stringPreferencesKey("ui_theme")
        val uiDynamicColorEnabled = booleanPreferencesKey("ui_dynamic_color_enabled")
        val uiFontScale = floatPreferencesKey("ui_font_scale")
        val uiGraphDensity = floatPreferencesKey("ui_graph_density")
        val uiProModeEnabled = booleanPreferencesKey("ui_pro_mode_enabled")

        val perfBatterySaverEnabled = booleanPreferencesKey("perf_battery_saver_enabled")
        val perfAdaptiveScanEnabled = booleanPreferencesKey("perf_adaptive_scan_enabled")
        val perfBackgroundScanLimitSec = intPreferencesKey("perf_background_scan_limit_sec")
        val perfThreadPriority = stringPreferencesKey("perf_thread_priority")

        val privacyLocalOnlyEnabled = booleanPreferencesKey("privacy_local_only_enabled")

        val devModeEnabled = booleanPreferencesKey("dev_mode_enabled")
        val devShowRawScanData = booleanPreferencesKey("dev_show_raw_scan_data")
        val devShowPhyMode = booleanPreferencesKey("dev_show_phy_mode")
        val devShowLinkSpeed = booleanPreferencesKey("dev_show_link_speed")
        val devExperimentalFlags = stringSetPreferencesKey("dev_experimental_flags")
    }

    private fun Int.toScanIntervalOrDefault(): ScanInterval {
        return ScanInterval.entries.firstOrNull { it.seconds == this } ?: WifiScanSettings().scanInterval
    }

    private fun String.toSignalUnitOrDefault(): SignalUnit {
        return runCatching { SignalUnit.valueOf(this) }.getOrDefault(WifiScanSettings().signalUnit)
    }

    private fun String.toSpeedUnitOrDefault(): SpeedUnit {
        return runCatching { SpeedUnit.valueOf(this) }.getOrDefault(SpeedTestSettings().speedUnit)
    }

    private fun String.toSpeedTestModeOrDefault(): SpeedTestMode {
        return runCatching { SpeedTestMode.valueOf(this) }.getOrDefault(SpeedTestSettings().mode)
    }

    private fun String.toServerSelectionOrDefault(): SpeedTestServerSelection {
        return runCatching { SpeedTestServerSelection.valueOf(this) }.getOrDefault(SpeedTestSettings().serverSelection)
    }

    private fun String.toGraphStyleOrDefault(): SpeedTestGraphStyle {
        return runCatching { SpeedTestGraphStyle.valueOf(this) }.getOrDefault(SpeedTestSettings().graphStyle)
    }

    private fun String.toHistoryRetentionOrDefault(): HistoryRetention {
        return runCatching { HistoryRetention.valueOf(this) }.getOrDefault(HistoryLogsSettings().retention)
    }

    private fun String.toExportFormatOrDefault(): ExportFormat {
        return runCatching { ExportFormat.valueOf(this) }.getOrDefault(HistoryLogsSettings().exportFormat)
    }

    private fun String.toSettingsThemeOrDefault(): SettingsTheme {
        return runCatching { SettingsTheme.valueOf(this) }.getOrDefault(UiUxSettings().theme)
    }

    private fun String.toThreadPriorityOrDefault(): ThreadPriority {
        return runCatching { ThreadPriority.valueOf(this) }.getOrDefault(PerformanceBatterySettings().threadPriority)
    }
}
