package com.srk.wifianalyzer.domain.usecase

import com.srk.wifianalyzer.domain.model.WifiScanState
import com.srk.wifianalyzer.domain.repository.WifiRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveWifiScanStateUseCase @Inject constructor(
    private val wifiRepository: WifiRepository,
) {
    operator fun invoke(): Flow<WifiScanState> = wifiRepository.observeWifiScanState()
}
