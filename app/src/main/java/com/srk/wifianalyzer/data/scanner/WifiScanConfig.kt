package com.srk.wifianalyzer.data.scanner

data class WifiScanConfig(
    val scanIntervalMs: Long,
) {
    companion object {
        val Default = WifiScanConfig(scanIntervalMs = 4_000)
    }
}
