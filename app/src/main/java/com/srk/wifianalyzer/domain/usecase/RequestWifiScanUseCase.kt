package com.srk.wifianalyzer.domain.usecase

import com.srk.wifianalyzer.domain.repository.WifiRepository
import javax.inject.Inject

class RequestWifiScanUseCase @Inject constructor(
    private val wifiRepository: WifiRepository,
) {
    suspend operator fun invoke(): Boolean = wifiRepository.requestWifiScan()
}
