package com.srk.wifianalyzer.data.scanner

import android.net.wifi.ScanResult

data class WifiScanSnapshot(
    val timestampMs: Long,
    val resultsUpdated: Boolean,
    val results: List<ScanResult>,
)
