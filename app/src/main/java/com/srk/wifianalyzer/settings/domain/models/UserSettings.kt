package com.srk.wifianalyzer.settings.domain.models

/**
 * Strongly-typed user settings persisted via DataStore.
 *
 * Defaults are chosen to be safe (battery-friendly) and to work well for basic users.
 */
data class UserSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val wifi: WifiScanSettings = WifiScanSettings(),
    val speedTest: SpeedTestSettings = SpeedTestSettings(),
    val alerts: AlertsAutomationSettings = AlertsAutomationSettings(),
    val history: HistoryLogsSettings = HistoryLogsSettings(),
    val ui: UiUxSettings = UiUxSettings(),
    val performance: PerformanceBatterySettings = PerformanceBatterySettings(),
    val privacy: PrivacySecuritySettings = PrivacySecuritySettings(),
    val developer: DeveloperSettings = DeveloperSettings(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

enum class ScanInterval(val seconds: Int) {
    S1(1),
    S3(3),
    S5(5),
    S10(10),
}

enum class SignalUnit {
    Dbm,
    Percentage,
}

enum class SpeedUnit {
    MBps,
    Mbps,
}

enum class SpeedTestMode {
    DownloadOnly,
    UploadOnly,
    Full,
}

enum class SpeedTestServerSelection {
    AutoNearest,
    Manual,
}

enum class SpeedTestGraphStyle {
    Line,
    Filled,
}

enum class SettingsTheme {
    System,
    Light,
    Dark,
}

enum class HistoryRetention {
    OneHour,
    TwentyFourHours,
    SevenDays,
}

enum class ExportFormat {
    Csv,
    Json,
}

enum class ThreadPriority {
    Normal,
    Background,
    High,
}

data class WifiScanSettings(
    val scanInterval: ScanInterval = ScanInterval.S3,
    val signalUnit: SignalUnit = SignalUnit.Dbm,
    val band2gEnabled: Boolean = true,
    val band5gEnabled: Boolean = true,
    val band6gEnabled: Boolean = true,
    val channelOverlapEnabled: Boolean = true,
    val rssiSmoothingEnabled: Boolean = true,
    /**
     * Exponential smoothing factor (0..1). Higher = smoother.
     */
    val rssiSmoothingAlpha: Float = 0.6f,
    val showChannelWidths: Set<Int> = setOf(20, 40, 80, 160),
)

data class SpeedTestSettings(
    val speedUnit: SpeedUnit = SpeedUnit.MBps,
    val mode: SpeedTestMode = SpeedTestMode.Full,
    val serverSelection: SpeedTestServerSelection = SpeedTestServerSelection.AutoNearest,
    val manualServerUrl: String = "",
    val jitterAndPacketLossEnabled: Boolean = false,
    val graphStyle: SpeedTestGraphStyle = SpeedTestGraphStyle.Line,
)

data class AlertsAutomationSettings(
    val weakSignalThresholdDbm: Int = -75,
    val wifiDisconnectNotificationEnabled: Boolean = false,
    val networkChangeDetectionEnabled: Boolean = true,
    val backgroundAutoScanEnabled: Boolean = false,
)

data class HistoryLogsSettings(
    val scanHistoryEnabled: Boolean = false,
    val retention: HistoryRetention = HistoryRetention.TwentyFourHours,
    val exportFormat: ExportFormat = ExportFormat.Csv,
    val autoClearOldLogs: Boolean = true,
)

data class UiUxSettings(
    val theme: SettingsTheme = SettingsTheme.System,
    val dynamicColorEnabled: Boolean = true,
    val fontScale: Float = 1.0f,
    val graphDensity: Float = 1.0f,
    val proModeEnabled: Boolean = false,
)

data class PerformanceBatterySettings(
    val batterySaverModeEnabled: Boolean = true,
    val adaptiveScanEngineEnabled: Boolean = true,
    /**
     * A hard cap for background scanning frequency.
     */
    val backgroundScanFrequencyLimitSec: Int = 60,
    val threadPriority: ThreadPriority = ThreadPriority.Background,
)

data class PrivacySecuritySettings(
    val localOnlyProcessingEnabled: Boolean = true,
)

data class DeveloperSettings(
    val developerModeEnabled: Boolean = false,
    val showRawScanData: Boolean = false,
    val showPhyMode: Boolean = false,
    val showLinkSpeed: Boolean = false,
    val experimentalFlags: Set<String> = emptySet(),
)
