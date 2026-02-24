package com.srk.wifianalyzer.presentation.apdetails

import com.srk.wifianalyzer.domain.model.WifiAccessPoint

data class AccessPointDetailsUiState(
    val bssid: String,
    val accessPoint: WifiAccessPoint? = null,
    val samples: List<RssiSample> = emptyList(),
    val similarAccessPoints: List<WifiAccessPoint> = emptyList(),
)
