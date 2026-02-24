package com.srk.wifianalyzer.domain.model

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val centerFreq0Mhz: Int? = null,
    val centerFreq1Mhz: Int? = null,
    val band: WifiBand,
    val channel: Int?,
    val channelWidth: WifiChannelWidth,
    val security: WifiSecurity,
    val standard: WifiStandard,
    val lastSeenMs: Long,
)
