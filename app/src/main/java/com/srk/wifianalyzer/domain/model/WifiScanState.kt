package com.srk.wifianalyzer.domain.model

data class WifiScanState(
    val isScanning: Boolean,
    val accessPoints: List<WifiAccessPoint>,
    val lastError: String?,
)
