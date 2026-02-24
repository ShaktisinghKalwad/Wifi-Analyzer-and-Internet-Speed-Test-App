package com.srk.wifianalyzer.presentation.apdetails

data class RssiSample(
    val timestampMs: Long,
    val rssiDbm: Int,
)
