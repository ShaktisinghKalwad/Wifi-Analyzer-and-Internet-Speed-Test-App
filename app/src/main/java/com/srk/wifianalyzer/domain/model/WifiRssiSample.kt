package com.srk.wifianalyzer.domain.model

data class WifiRssiSample(
    val timestampMs: Long,
    val rssiDbm: Int,
)
