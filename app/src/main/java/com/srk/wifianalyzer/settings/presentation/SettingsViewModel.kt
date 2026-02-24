package com.srk.wifianalyzer.settings.presentation

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srk.wifianalyzer.R
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import com.srk.wifianalyzer.settings.domain.models.ExportFormat
import com.srk.wifianalyzer.settings.domain.models.HistoryRetention
import com.srk.wifianalyzer.settings.domain.models.ScanInterval
import com.srk.wifianalyzer.settings.domain.models.SettingsTheme
import com.srk.wifianalyzer.settings.domain.models.SignalUnit
import com.srk.wifianalyzer.settings.domain.models.SpeedTestGraphStyle
import com.srk.wifianalyzer.settings.domain.models.SpeedTestMode
import com.srk.wifianalyzer.settings.domain.models.SpeedTestServerSelection
import com.srk.wifianalyzer.settings.domain.models.SpeedUnit
import com.srk.wifianalyzer.settings.domain.models.ThreadPriority
import com.srk.wifianalyzer.settings.domain.models.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    data class UiState(
        val settings: UserSettings = UserSettings(),
        val developerModeTapProgress: Int = 0,
    )

    sealed interface Effect {
        data class ShowMessageRes(
            @StringRes val resId: Int,
            val formatArgs: List<Any> = emptyList(),
        ) : Effect
        object OpenAppSettings : Effect
    }

    private val developerTapProgress = MutableStateFlow(0)

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    val uiState: StateFlow<UiState> = combine(
        settingsRepository.userSettings,
        developerTapProgress,
    ) { settings, tapProgress ->
        UiState(settings = settings, developerModeTapProgress = tapProgress)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    private var lastVersionTapElapsedRealtime: Long = 0L

    fun onAppVersionTapped() {
        val current = uiState.value.settings
        if (current.developer.developerModeEnabled) {
            developerTapProgress.value = 0
            _effects.tryEmit(Effect.ShowMessageRes(R.string.settings_dev_already_enabled))
            return
        }

        val now = SystemClock.elapsedRealtime()
        val resetWindowMs = 1_500L
        val withinWindow = (now - lastVersionTapElapsedRealtime) <= resetWindowMs
        lastVersionTapElapsedRealtime = now

        val nextProgress = if (withinWindow) developerTapProgress.value + 1 else 1
        if (nextProgress >= 7) {
            developerTapProgress.value = 0
            update { settings ->
                settings.copy(developer = settings.developer.copy(developerModeEnabled = true))
            }
            _effects.tryEmit(Effect.ShowMessageRes(R.string.settings_dev_enabled))
        } else {
            developerTapProgress.value = nextProgress
            _effects.tryEmit(
                Effect.ShowMessageRes(
                    resId = R.string.settings_dev_taps_remaining,
                    formatArgs = listOf(7 - nextProgress),
                )
            )
        }
    }

    fun openAppSettings() {
        _effects.tryEmit(Effect.OpenAppSettings)
    }

    fun resetToDefaults() {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.resetToDefaults()
        }
    }

    fun setProModeEnabled(enabled: Boolean) {
        update { settings -> settings.copy(ui = settings.ui.copy(proModeEnabled = enabled)) }
    }

    fun setTheme(theme: SettingsTheme) {
        update { settings -> settings.copy(ui = settings.ui.copy(theme = theme)) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        update { settings -> settings.copy(ui = settings.ui.copy(dynamicColorEnabled = enabled)) }
    }

    fun setFontScale(fontScale: Float) {
        update { settings -> settings.copy(ui = settings.ui.copy(fontScale = fontScale)) }
    }

    fun setGraphDensity(graphDensity: Float) {
        update { settings -> settings.copy(ui = settings.ui.copy(graphDensity = graphDensity)) }
    }

    fun setScanInterval(interval: ScanInterval) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(scanInterval = interval)) }
    }

    fun setSignalUnit(unit: SignalUnit) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(signalUnit = unit)) }
    }

    fun setBand2gEnabled(enabled: Boolean) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(band2gEnabled = enabled)) }
    }

    fun setBand5gEnabled(enabled: Boolean) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(band5gEnabled = enabled)) }
    }

    fun setBand6gEnabled(enabled: Boolean) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(band6gEnabled = enabled)) }
    }

    fun setChannelOverlapEnabled(enabled: Boolean) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(channelOverlapEnabled = enabled)) }
    }

    fun setRssiSmoothingEnabled(enabled: Boolean) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(rssiSmoothingEnabled = enabled)) }
    }

    fun setRssiSmoothingAlpha(alpha: Float) {
        update { settings -> settings.copy(wifi = settings.wifi.copy(rssiSmoothingAlpha = alpha)) }
    }

    fun setChannelWidthShown(widthMhz: Int, shown: Boolean) {
        update { settings ->
            val next = settings.wifi.showChannelWidths.toMutableSet()
            if (shown) next.add(widthMhz) else next.remove(widthMhz)
            settings.copy(wifi = settings.wifi.copy(showChannelWidths = next))
        }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        update { settings -> settings.copy(speedTest = settings.speedTest.copy(speedUnit = unit)) }
    }

    fun setSpeedTestMode(mode: SpeedTestMode) {
        update { settings -> settings.copy(speedTest = settings.speedTest.copy(mode = mode)) }
    }

    fun setServerSelection(selection: SpeedTestServerSelection) {
        update { settings -> settings.copy(speedTest = settings.speedTest.copy(serverSelection = selection)) }
    }

    fun setManualServerUrl(url: String) {
        update { settings -> settings.copy(speedTest = settings.speedTest.copy(manualServerUrl = url)) }
    }

    fun setJitterAndPacketLossEnabled(enabled: Boolean) {
        update { settings -> settings.copy(speedTest = settings.speedTest.copy(jitterAndPacketLossEnabled = enabled)) }
    }

    fun setSpeedTestGraphStyle(style: SpeedTestGraphStyle) {
        update { settings -> settings.copy(speedTest = settings.speedTest.copy(graphStyle = style)) }
    }

    fun setWeakSignalThresholdDbm(thresholdDbm: Int) {
        update { settings -> settings.copy(alerts = settings.alerts.copy(weakSignalThresholdDbm = thresholdDbm)) }
    }

    fun setWifiDisconnectNotificationEnabled(enabled: Boolean) {
        update { settings -> settings.copy(alerts = settings.alerts.copy(wifiDisconnectNotificationEnabled = enabled)) }
    }

    fun setNetworkChangeDetectionEnabled(enabled: Boolean) {
        update { settings -> settings.copy(alerts = settings.alerts.copy(networkChangeDetectionEnabled = enabled)) }
    }

    fun setBackgroundAutoScanEnabled(enabled: Boolean) {
        update { settings -> settings.copy(alerts = settings.alerts.copy(backgroundAutoScanEnabled = enabled)) }
    }

    fun setScanHistoryEnabled(enabled: Boolean) {
        update { settings -> settings.copy(history = settings.history.copy(scanHistoryEnabled = enabled)) }
    }

    fun setHistoryRetention(retention: HistoryRetention) {
        update { settings -> settings.copy(history = settings.history.copy(retention = retention)) }
    }

    fun setExportFormat(format: ExportFormat) {
        update { settings -> settings.copy(history = settings.history.copy(exportFormat = format)) }
    }

    fun setAutoClearOldLogs(enabled: Boolean) {
        update { settings -> settings.copy(history = settings.history.copy(autoClearOldLogs = enabled)) }
    }

    fun setBatterySaverEnabled(enabled: Boolean) {
        update { settings -> settings.copy(performance = settings.performance.copy(batterySaverModeEnabled = enabled)) }
    }

    fun setAdaptiveScanEngineEnabled(enabled: Boolean) {
        update { settings -> settings.copy(performance = settings.performance.copy(adaptiveScanEngineEnabled = enabled)) }
    }

    fun setBackgroundScanFrequencyLimitSec(seconds: Int) {
        update { settings -> settings.copy(performance = settings.performance.copy(backgroundScanFrequencyLimitSec = seconds)) }
    }

    fun setThreadPriority(priority: ThreadPriority) {
        update { settings -> settings.copy(performance = settings.performance.copy(threadPriority = priority)) }
    }

    fun setLocalOnlyProcessingEnabled(enabled: Boolean) {
        update { settings -> settings.copy(privacy = settings.privacy.copy(localOnlyProcessingEnabled = enabled)) }
    }

    fun setShowRawScanData(enabled: Boolean) {
        update { settings -> settings.copy(developer = settings.developer.copy(showRawScanData = enabled)) }
    }

    fun setShowPhyMode(enabled: Boolean) {
        update { settings -> settings.copy(developer = settings.developer.copy(showPhyMode = enabled)) }
    }

    fun setShowLinkSpeed(enabled: Boolean) {
        update { settings -> settings.copy(developer = settings.developer.copy(showLinkSpeed = enabled)) }
    }

    private fun update(reducer: (UserSettings) -> UserSettings) {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.updateUserSettings(reducer)
        }
    }
}
