package com.srk.wifianalyzer.presentation.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srk.wifianalyzer.domain.analysis.ChannelAnalysis
import com.srk.wifianalyzer.domain.analysis.ChannelAnalysisOptions
import com.srk.wifianalyzer.domain.analysis.ComputeChannelAnalysisUseCase
import com.srk.wifianalyzer.domain.model.WifiBand
import com.srk.wifianalyzer.domain.model.WifiChannelWidth
import com.srk.wifianalyzer.domain.usecase.ObserveWifiScanStateUseCase
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChannelViewModel @Inject constructor(
    observeWifiScanStateUseCase: ObserveWifiScanStateUseCase,
    private val computeChannelAnalysisUseCase: ComputeChannelAnalysisUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selectedBand = MutableStateFlow(WifiBand.Band2G)
    private val options = MutableStateFlow(ChannelAnalysisOptions())

    init {
        viewModelScope.launch {
            settingsRepository.userSettings
                .map { it.wifi.showChannelWidths }
                .distinctUntilChanged()
                .collect { allowed ->
                    val current = options.value.recommendationWidth
                    if (current.mhz !in allowed) {
                        val next = chooseWidth(allowed)
                        if (next != null) {
                            options.value = options.value.copy(recommendationWidth = next)
                        }
                    }
                }
        }
    }

    val uiState: StateFlow<ChannelUiState> = combine(
        selectedBand,
        options,
        observeWifiScanStateUseCase(),
        settingsRepository.userSettings,
    ) { band, options, scanState, settings ->
            val analysis: ChannelAnalysis? = if (scanState.accessPoints.isNotEmpty()) {
                computeChannelAnalysisUseCase(
                    band = band,
                    accessPoints = scanState.accessPoints,
                    options = options,
                )
            } else {
                null
            }

            val recommended20 = if (scanState.accessPoints.isNotEmpty()) {
                computeChannelAnalysisUseCase(
                    band = band,
                    accessPoints = scanState.accessPoints,
                    options = options.copy(recommendationWidth = WifiChannelWidth.W20),
                ).bestChannels
            } else {
                emptyList()
            }

            val recommended80 = if (scanState.accessPoints.isNotEmpty() && band != WifiBand.Band2G) {
                computeChannelAnalysisUseCase(
                    band = band,
                    accessPoints = scanState.accessPoints,
                    options = options.copy(recommendationWidth = WifiChannelWidth.W80),
                ).bestChannels
            } else {
                emptyList()
            }

            ChannelUiState(
                selectedBand = band,
                options = options,
                channelOverlapEnabled = settings.wifi.channelOverlapEnabled,
                allowedChannelWidthsMhz = settings.wifi.showChannelWidths,
                analysis = analysis,
                recommended20 = recommended20,
                recommended80 = recommended80,
                lastError = scanState.lastError,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ChannelUiState(),
        )

    fun selectBand(band: WifiBand) {
        selectedBand.value = band
    }

    fun setAvoidDfs(enabled: Boolean) {
        options.value = options.value.copy(avoidDfs = enabled)
    }

    fun setPreferNonOverlapping2g(enabled: Boolean) {
        options.value = options.value.copy(preferNonOverlapping2g = enabled)
    }

    fun setRecommendationWidth(width: WifiChannelWidth) {
        options.value = options.value.copy(recommendationWidth = width)
    }

    fun setPreferPsc6g(enabled: Boolean) {
        options.value = options.value.copy(preferPsc6g = enabled)
    }

     private fun chooseWidth(allowedMhz: Set<Int>): WifiChannelWidth? {
         val allowed = allowedMhz.ifEmpty { setOf(20, 40, 80, 160) }
         return when {
             20 in allowed -> WifiChannelWidth.W20
             40 in allowed -> WifiChannelWidth.W40
             80 in allowed -> WifiChannelWidth.W80
             160 in allowed -> WifiChannelWidth.W160
             else -> null
         }
     }
}
