package com.srk.wifianalyzer.presentation.scanner

import com.srk.wifianalyzer.domain.model.WifiAccessPoint
import com.srk.wifianalyzer.settings.domain.models.SignalUnit

data class ScannerUiState(
    val isScanning: Boolean = false,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val lastError: String? = null,
    val signalUnit: SignalUnit = SignalUnit.Dbm,
)
