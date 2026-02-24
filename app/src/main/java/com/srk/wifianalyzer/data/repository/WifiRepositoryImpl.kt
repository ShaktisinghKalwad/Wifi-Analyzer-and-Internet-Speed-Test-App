package com.srk.wifianalyzer.data.repository

import com.srk.wifianalyzer.data.mapper.toDomain
import com.srk.wifianalyzer.data.scanner.WifiScanDataSource
import com.srk.wifianalyzer.data.scanner.WifiScanConfig
import com.srk.wifianalyzer.data.scanner.WifiScanSnapshot
import com.srk.wifianalyzer.domain.model.WifiRssiSample
import com.srk.wifianalyzer.domain.model.WifiScanState
import com.srk.wifianalyzer.domain.repository.WifiRepository
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import com.srk.wifianalyzer.settings.domain.models.HistoryRetention
import com.srk.wifianalyzer.settings.domain.models.UserSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn

@Singleton
class WifiRepositoryImpl @Inject constructor(
    private val wifiScanDataSource: WifiScanDataSource,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope,
) : WifiRepository {

    private val rssiHistoryByBssid = MutableStateFlow<Map<String, List<WifiRssiSample>>>(emptyMap())

    private val scanStateFlow = settingsRepository.userSettings
        .map { settings ->
            val requestedSeconds = settings.wifi.scanInterval.seconds
            val effectiveSeconds = if (settings.performance.batterySaverModeEnabled) {
                maxOf(requestedSeconds, 5)
            } else {
                requestedSeconds
            }
            WifiScanConfig(scanIntervalMs = effectiveSeconds.toLong() * 1_000L)
        }
        .distinctUntilChanged()
        .flatMapLatest { config ->
            wifiScanDataSource.observeScanSnapshots(config)
        }
        .map { snapshot -> snapshot.toScanState() }

    private val sharedState = combine(
        scanStateFlow,
        settingsRepository.userSettings,
    ) { scanState, settings ->
        scanState to settings
    }
        .onEach { (state, settings) ->
            updateRssiHistory(state, settings)
        }
        .map { (state, _) -> state }
        .catch { e ->
            emit(
                WifiScanState(
                    isScanning = false,
                    accessPoints = emptyList(),
                    lastError = e.message ?: "Wi‑Fi scan failed",
                )
            )
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = WifiScanState(
                isScanning = false,
                accessPoints = emptyList(),
                lastError = null,
            )
        )

    override fun observeWifiScanState(): Flow<WifiScanState> = sharedState

    override fun observeRssiHistory(bssid: String): Flow<List<WifiRssiSample>> {
        val key = bssid.trim().lowercase()
        return rssiHistoryByBssid
            .map { map -> map[key].orEmpty() }
            .distinctUntilChanged()
    }

    override suspend fun requestWifiScan(): Boolean {
        return wifiScanDataSource.requestImmediateScan()
    }

    private fun updateRssiHistory(state: WifiScanState, settings: UserSettings) {
        if (!settings.history.scanHistoryEnabled) {
            if (rssiHistoryByBssid.value.isNotEmpty()) {
                rssiHistoryByBssid.value = emptyMap()
            }
            return
        }
        if (state.accessPoints.isEmpty()) return

        val nowMs = System.currentTimeMillis()

        val windowMs = when (settings.history.retention) {
            HistoryRetention.OneHour -> 60L * 60L * 1000L
            HistoryRetention.TwentyFourHours -> 24L * 60L * 60L * 1000L
            HistoryRetention.SevenDays -> 7L * 24L * 60L * 60L * 1000L
        }

        val intervalMs = (settings.wifi.scanInterval.seconds.toLong().coerceAtLeast(1L)) * 1_000L
        val computedMaxSamples = ((windowMs / intervalMs).toInt() + 16).coerceAtLeast(240)
        val maxSamples = computedMaxSamples.coerceAtMost(10_000)

        val current = rssiHistoryByBssid.value
        val mutable = current.toMutableMap()

        state.accessPoints.forEach { ap ->
            val key = ap.bssid.trim().lowercase()
            if (key.isBlank()) return@forEach

            val existing = mutable[key].orEmpty()
            val sample = WifiRssiSample(timestampMs = ap.lastSeenMs, rssiDbm = ap.rssiDbm)

            val appended = when {
                existing.isEmpty() -> listOf(sample)
                existing.last().timestampMs == sample.timestampMs -> existing.dropLast(1) + sample
                else -> existing + sample
            }

            val trimmed = if (settings.history.autoClearOldLogs) {
                appended
                    .filter { it.timestampMs >= (nowMs - windowMs) }
                    .takeLast(maxSamples)
            } else {
                appended.takeLast(maxSamples)
            }

            mutable[key] = trimmed
        }

        rssiHistoryByBssid.value = mutable
    }
}

private fun WifiScanSnapshot.toScanState(): WifiScanState {
    val now = timestampMs
    val aps = results
        .asSequence()
        .map { it.toDomain(now) }
        .filter { it.bssid.isNotBlank() }
        .toList()
        .groupBy { ap -> "${ap.bssid.trim().lowercase()}:${ap.frequencyMhz}" }
        .values
        .mapNotNull { sameAp -> sameAp.maxByOrNull { it.rssiDbm } }
        .sortedByDescending { it.rssiDbm }

    val err = if (!resultsUpdated && aps.isEmpty()) {
        "No scan results yet. Ensure Wi‑Fi is ON, required permissions are granted, and Location services are enabled (some devices require it)."
    } else {
        null
    }

    return WifiScanState(
        isScanning = true,
        accessPoints = aps,
        lastError = err,
    )
}
