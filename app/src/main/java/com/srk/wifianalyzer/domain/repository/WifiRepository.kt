package com.srk.wifianalyzer.domain.repository

import com.srk.wifianalyzer.domain.model.WifiRssiSample
import com.srk.wifianalyzer.domain.model.WifiScanState
import kotlinx.coroutines.flow.Flow

interface WifiRepository {
    fun observeWifiScanState(): Flow<WifiScanState>

    fun observeRssiHistory(bssid: String): Flow<List<WifiRssiSample>>

    suspend fun requestWifiScan(): Boolean
}
