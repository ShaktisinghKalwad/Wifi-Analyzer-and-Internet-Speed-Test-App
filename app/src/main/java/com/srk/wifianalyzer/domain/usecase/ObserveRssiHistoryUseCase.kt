package com.srk.wifianalyzer.domain.usecase

import com.srk.wifianalyzer.domain.model.WifiRssiSample
import com.srk.wifianalyzer.domain.repository.WifiRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveRssiHistoryUseCase @Inject constructor(
    private val wifiRepository: WifiRepository,
) {
    operator fun invoke(bssid: String): Flow<List<WifiRssiSample>> = wifiRepository.observeRssiHistory(bssid)
}
