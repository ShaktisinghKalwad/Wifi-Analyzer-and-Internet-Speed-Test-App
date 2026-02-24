package com.srk.wifianalyzer.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srk.wifianalyzer.domain.usecase.ObserveWifiScanStateUseCase
import com.srk.wifianalyzer.domain.usecase.RequestWifiScanUseCase
import com.srk.wifianalyzer.domain.model.WifiAccessPoint
import com.srk.wifianalyzer.domain.model.WifiBand
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import com.srk.wifianalyzer.settings.domain.models.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@HiltViewModel
class ScannerViewModel @Inject constructor(
    observeWifiScanStateUseCase: ObserveWifiScanStateUseCase,
    private val requestWifiScanUseCase: RequestWifiScanUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val emaByBssid = mutableMapOf<String, Float>()

    val uiState: StateFlow<ScannerUiState> = combine(
        observeWifiScanStateUseCase(),
        settingsRepository.userSettings,
    ) { scanState, settings ->
        val filtered = applyBandFilters(scanState.accessPoints, settings)
        val smoothed = applyRssiSmoothing(filtered, settings)
            .sortedByDescending { it.rssiDbm }

        ScannerUiState(
            isScanning = scanState.isScanning,
            accessPoints = smoothed,
            lastError = scanState.lastError,
            signalUnit = settings.wifi.signalUnit,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ScannerUiState(isScanning = false)
        )

    fun onPullToRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            requestWifiScanUseCase()
            delay(1_200L)
            _isRefreshing.value = false
        }
    }

    private fun applyBandFilters(
        accessPoints: List<WifiAccessPoint>,
        settings: UserSettings,
    ): List<WifiAccessPoint> {
        val wifi = settings.wifi
        return accessPoints.filter { ap ->
            when (ap.band) {
                WifiBand.Band2G -> wifi.band2gEnabled
                WifiBand.Band5G -> wifi.band5gEnabled
                WifiBand.Band6G -> wifi.band6gEnabled
                WifiBand.Unknown -> true
            }
        }
    }

    private fun applyRssiSmoothing(
        accessPoints: List<WifiAccessPoint>,
        settings: UserSettings,
    ): List<WifiAccessPoint> {
        val wifi = settings.wifi
        if (!wifi.rssiSmoothingEnabled) {
            emaByBssid.clear()
            return accessPoints
        }

        val alpha = wifi.rssiSmoothingAlpha.coerceIn(0f, 1f)
        return accessPoints.map { ap ->
            val key = ap.bssid.trim().lowercase()
            val prev = emaByBssid[key]
            val current = ap.rssiDbm.toFloat()
            val smoothed = if (prev == null) {
                current
            } else {
                alpha * prev + (1f - alpha) * current
            }
            emaByBssid[key] = smoothed
            ap.copy(rssiDbm = smoothed.roundToInt())
        }
    }
}
