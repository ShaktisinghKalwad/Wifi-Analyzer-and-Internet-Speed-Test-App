package com.srk.wifianalyzer.data.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class WifiScanDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiManager: WifiManager,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun requestImmediateScan(): Boolean = withContext(ioDispatcher) {
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (_: Throwable) {
            false
        }
    }

    fun observeScanSnapshots(config: WifiScanConfig = WifiScanConfig.Default): Flow<WifiScanSnapshot> = callbackFlow {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val broadcastUpdated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                val results = try {
                    wifiManager.scanResults
                } catch (_: Throwable) {
                    emptyList()
                }
                val resultsUpdated = broadcastUpdated && results.isNotEmpty()

                trySend(
                    WifiScanSnapshot(
                        timestampMs = System.currentTimeMillis(),
                        resultsUpdated = resultsUpdated,
                        results = results,
                    )
                )
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        val initialResults = try {
            wifiManager.scanResults
        } catch (_: Throwable) {
            emptyList()
        }
        trySend(
            WifiScanSnapshot(
                timestampMs = System.currentTimeMillis(),
                resultsUpdated = initialResults.isNotEmpty(),
                results = initialResults,
            )
        )

        val scanJob = launch(ioDispatcher) {
            val minIntervalMs = if (Build.VERSION.SDK_INT >= 29) 30_000L else 0L
            val baselineIntervalMs = maxOf(config.scanIntervalMs, minIntervalMs)
            var intervalMs = baselineIntervalMs

            while (true) {
                try {
                    @Suppress("DEPRECATION")
                    val ok = wifiManager.startScan()
                    if (!ok) {
                        val results = try {
                            wifiManager.scanResults
                        } catch (_: Throwable) {
                            emptyList()
                        }
                        trySend(
                            WifiScanSnapshot(
                                timestampMs = System.currentTimeMillis(),
                                resultsUpdated = false,
                                results = results,
                            )
                        )
                        intervalMs = minOf(intervalMs * 2, 120_000L)
                    } else {
                        intervalMs = baselineIntervalMs
                    }
                } catch (_: Throwable) {
                    intervalMs = minOf(intervalMs * 2, 120_000L)
                }
                delay(intervalMs)
            }
        }

        awaitClose {
            scanJob.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }
    }.flowOn(ioDispatcher)
}
