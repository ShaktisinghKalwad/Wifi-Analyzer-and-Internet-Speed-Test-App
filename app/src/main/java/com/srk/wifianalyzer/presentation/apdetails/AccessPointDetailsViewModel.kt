package com.srk.wifianalyzer.presentation.apdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srk.wifianalyzer.domain.model.WifiRssiSample
import com.srk.wifianalyzer.domain.usecase.ObserveRssiHistoryUseCase
import com.srk.wifianalyzer.domain.usecase.ObserveWifiScanStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AccessPointDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeWifiScanStateUseCase: ObserveWifiScanStateUseCase,
    observeRssiHistoryUseCase: ObserveRssiHistoryUseCase,
) : ViewModel() {

    private val bssid: String = checkNotNull(savedStateHandle["bssid"]).toString()

    private val scanStateFlow = observeWifiScanStateUseCase()

    private val accessPointFlow = scanStateFlow
        .map { scanState ->
            scanState.accessPoints.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
        }
        .distinctUntilChanged()

    private val similarFlow = scanStateFlow
        .map { scanState ->
            val ap = scanState.accessPoints.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
            if (ap == null) return@map emptyList()

            scanState.accessPoints
                .asSequence()
                .filter { it.ssid == ap.ssid }
                .filterNot { it.bssid.equals(ap.bssid, ignoreCase = true) }
                .sortedByDescending { it.rssiDbm }
                .take(6)
                .toList()
        }
        .distinctUntilChanged()

    private val historyFlow = observeRssiHistoryUseCase(bssid)
        .map { list -> list.map { it.toUiSample() } }

    val uiState: StateFlow<AccessPointDetailsUiState> = combine(accessPointFlow, historyFlow, similarFlow) { ap, samples, similar ->
        AccessPointDetailsUiState(
            bssid = bssid,
            accessPoint = ap,
            samples = samples,
            similarAccessPoints = similar,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AccessPointDetailsUiState(bssid = bssid),
        )
}

private fun WifiRssiSample.toUiSample(): RssiSample = RssiSample(
    timestampMs = timestampMs,
    rssiDbm = rssiDbm,
)
