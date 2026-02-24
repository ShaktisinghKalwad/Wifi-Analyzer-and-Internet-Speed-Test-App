package com.srk.wifianalyzer.data.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SpeedTestEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope,
) {

    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    private val runGuard = AtomicLong(0L)
    private val runStartElapsedMs = AtomicLong(0L)
    private var testJob: Job? = null

    private companion object {
        private const val MAX_SAMPLES = 160
        private const val BYTES_PER_MB = 1_000_000.0
    }

    private fun Double.isFiniteValue(): Boolean = !this.isNaN() && !this.isInfinite()

    fun startFullTest(config: SpeedTestConfig = SpeedTestConfig()): Boolean {
        synchronized(this) {
            if (testJob?.isActive == true) return false
            runGuard.incrementAndGet()
            runStartElapsedMs.set(SystemClock.elapsedRealtime())

            val includeIntermission = config.downloadDurationMs > 0L && config.uploadDurationMs > 0L
            val plannedTotalMs = config.countdownSeconds * 1_000L +
                config.downloadDurationMs +
                (if (includeIntermission) config.intermissionMs else 0L) +
                config.uploadDurationMs

            testJob = appScope.launch(SupervisorJob() + ioDispatcher) {
                try {
                    ensureNetworkAvailable()

                    runCountdown(
                        seconds = config.countdownSeconds,
                        plannedTotalMs = plannedTotalMs,
                        updateIntervalMs = config.updateIntervalMs,
                    )

                    if (config.downloadDurationMs > 0L) {
                        runDownloadTest(config = config, plannedTotalMs = plannedTotalMs)
                    }
                    if (includeIntermission && config.intermissionMs > 0L) {
                        runIntermission(
                            plannedTotalMs = plannedTotalMs,
                            intermissionMs = config.intermissionMs,
                            updateIntervalMs = config.updateIntervalMs,
                        )
                    }
                    if (config.uploadDurationMs > 0L) {
                        runUploadTest(config = config, plannedTotalMs = plannedTotalMs)
                    }

                    _state.value = _state.value.copy(
                        stage = SpeedTestStage.Completed,
                        totalDurationMs = SystemClock.elapsedRealtime() - runStartElapsedMs.get(),
                        plannedTotalDurationMs = plannedTotalMs,
                        phaseProgress = 1f,
                        totalProgress = 1f,
                        message = null,
                    )
                } catch (ce: CancellationException) {
                    _state.value = SpeedTestState()
                    throw ce
                } catch (t: Throwable) {
                    _state.value = _state.value.copy(
                        stage = SpeedTestStage.Error,
                        plannedTotalDurationMs = plannedTotalMs,
                        message = t.message ?: "Speed test failed",
                    )
                }
            }
            return true
        }
    }

    fun stop() {
        synchronized(this) {
            testJob?.cancel()
            testJob = null
            runStartElapsedMs.set(0L)
            _state.value = SpeedTestState()
        }
    }

    private suspend fun runCountdown(
        seconds: Int,
        plannedTotalMs: Long,
        updateIntervalMs: Long,
    ) {
        if (seconds <= 0) return

        val startMs = SystemClock.elapsedRealtime()
        val endMs = startMs + seconds * 1_000L

        while (SystemClock.elapsedRealtime() < endMs) {
            ensureNetworkAvailable()

            val nowMs = SystemClock.elapsedRealtime()
            val remainingMs = (endMs - nowMs).coerceAtLeast(0L)
            val remainingSec = ((remainingMs + 999L) / 1_000L).toInt().coerceAtLeast(1)

            val totalElapsed = nowMs - runStartElapsedMs.get()
            val totalProgress = if (plannedTotalMs > 0L) {
                (totalElapsed.toDouble() / plannedTotalMs.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            val phaseProgress = 1f - (remainingMs.toDouble() / (seconds * 1_000.0)).toFloat()

            _state.value = SpeedTestState(
                stage = SpeedTestStage.Countdown,
                countdownRemainingSec = remainingSec,
                plannedTotalDurationMs = plannedTotalMs,
                totalDurationMs = totalElapsed,
                phaseProgress = phaseProgress.coerceIn(0f, 1f),
                totalProgress = totalProgress,
            )

            delay(updateIntervalMs.coerceIn(250L, 500L))
        }
    }

    private suspend fun runIntermission(
        plannedTotalMs: Long,
        intermissionMs: Long,
        updateIntervalMs: Long,
    ) {
        if (intermissionMs <= 0L) return

        val startMs = SystemClock.elapsedRealtime()
        val endMs = startMs + intermissionMs

        while (SystemClock.elapsedRealtime() < endMs) {
            ensureNetworkAvailable()

            val nowMs = SystemClock.elapsedRealtime()
            val elapsedPhaseMs = nowMs - startMs
            val phaseProgress = (elapsedPhaseMs.toDouble() / intermissionMs.toDouble()).toFloat().coerceIn(0f, 1f)

            val totalElapsed = nowMs - runStartElapsedMs.get()
            val totalProgress = if (plannedTotalMs > 0L) {
                (totalElapsed.toDouble() / plannedTotalMs.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            val prev = _state.value
            _state.value = prev.copy(
                stage = SpeedTestStage.PreparingUpload,
                plannedTotalDurationMs = plannedTotalMs,
                totalDurationMs = totalElapsed,
                phaseProgress = phaseProgress,
                totalProgress = totalProgress,
                countdownRemainingSec = 0,
            )

            delay(updateIntervalMs.coerceIn(250L, 500L))
        }
    }

    private suspend fun runDownloadTest(config: SpeedTestConfig, plannedTotalMs: Long) {
        _state.value = SpeedTestState(
            stage = SpeedTestStage.Downloading,
            plannedTotalDurationMs = plannedTotalMs,
        )

        val bytes = AtomicLong(0L)
        val startMs = SystemClock.elapsedRealtime()
        val deadlineMs = startMs + config.downloadDurationMs
        val error = AtomicReference<Throwable?>(null)

        supervisorScope {
            repeat(config.parallelDownloads.coerceIn(1, 4)) {
                launch {
                    try {
                        downloadWorker(
                            url = buildDownloadUrl(config),
                            deadlineMs = deadlineMs,
                            bytesCounter = bytes,
                        )
                    } catch (t: Throwable) {
                        if (t !is CancellationException) error.compareAndSet(null, t)
                        throw t
                    }
                }
            }

            val updateJob = launch {
                emitLoop(
                    stage = SpeedTestStage.Downloading,
                    startMs = startMs,
                    deadlineMs = deadlineMs,
                    phaseDurationMs = config.downloadDurationMs,
                    plannedTotalMs = plannedTotalMs,
                    bytesCounter = bytes,
                    updateIntervalMs = config.updateIntervalMs,
                    updateDirection = Direction.Download,
                    smoothingTimeConstantSec = config.smoothingTimeConstantSec,
                    minSampleSeconds = config.minSampleSeconds,
                )
            }

            while (isActive && SystemClock.elapsedRealtime() < deadlineMs) {
                error.get()?.let { throw it }
                delay(120)
            }

            updateJob.join()
        }

        val elapsedSeconds = max(0.001, (SystemClock.elapsedRealtime() - startMs).toDouble() / 1000.0)
        val totalBytes = bytes.get()
        val avgMBps = megabytesPerSecond(totalBytes, elapsedSeconds)

        val prev = _state.value
        _state.value = prev.copy(
            download = prev.download.copy(
                bytes = totalBytes,
                avgMBps = avgMBps,
                currentMBps = 0.0,
            ),
            totalDurationMs = SystemClock.elapsedRealtime() - runStartElapsedMs.get(),
            plannedTotalDurationMs = plannedTotalMs,
            phaseProgress = 1f,
            totalBytesUsed = totalBytes + prev.upload.bytes,
        )
    }

    private suspend fun runUploadTest(config: SpeedTestConfig, plannedTotalMs: Long) {
        val prev = _state.value
        _state.value = prev.copy(
            stage = SpeedTestStage.Uploading,
            plannedTotalDurationMs = plannedTotalMs,
            phaseProgress = 0f,
            countdownRemainingSec = 0,
            message = null,
        )

        val bytes = AtomicLong(0L)
        val startMs = SystemClock.elapsedRealtime()
        val deadlineMs = startMs + config.uploadDurationMs
        val error = AtomicReference<Throwable?>(null)

        supervisorScope {
            repeat(config.parallelUploads.coerceIn(1, 4)) {
                launch {
                    try {
                        uploadWorker(
                            url = config.uploadUrl,
                            deadlineMs = deadlineMs,
                            bytesCounter = bytes,
                            targetBytesPerRequest = config.uploadBytesPerRequest,
                        )
                    } catch (t: Throwable) {
                        if (t !is CancellationException) error.compareAndSet(null, t)
                        throw t
                    }
                }
            }

            val updateJob = launch {
                emitLoop(
                    stage = SpeedTestStage.Uploading,
                    startMs = startMs,
                    deadlineMs = deadlineMs,
                    phaseDurationMs = config.uploadDurationMs,
                    plannedTotalMs = plannedTotalMs,
                    bytesCounter = bytes,
                    updateIntervalMs = config.updateIntervalMs,
                    updateDirection = Direction.Upload,
                    smoothingTimeConstantSec = config.smoothingTimeConstantSec,
                    minSampleSeconds = config.minSampleSeconds,
                )
            }

            while (isActive && SystemClock.elapsedRealtime() < deadlineMs) {
                error.get()?.let { throw it }
                delay(120)
            }

            updateJob.join()
        }

        val elapsedSeconds = max(0.001, (SystemClock.elapsedRealtime() - startMs).toDouble() / 1000.0)
        val totalBytes = bytes.get()
        val avgMBps = megabytesPerSecond(totalBytes, elapsedSeconds)

        val post = _state.value
        _state.value = post.copy(
            upload = post.upload.copy(
                bytes = totalBytes,
                avgMBps = avgMBps,
                currentMBps = 0.0,
            ),
            totalDurationMs = SystemClock.elapsedRealtime() - runStartElapsedMs.get(),
            plannedTotalDurationMs = plannedTotalMs,
            phaseProgress = 1f,
            totalBytesUsed = post.download.bytes + totalBytes,
        )
    }

    private suspend fun emitLoop(
        stage: SpeedTestStage,
        startMs: Long,
        deadlineMs: Long,
        phaseDurationMs: Long,
        plannedTotalMs: Long,
        bytesCounter: AtomicLong,
        updateIntervalMs: Long,
        updateDirection: Direction,
        smoothingTimeConstantSec: Double,
        minSampleSeconds: Double,
    ) {
        var lastTickMs = SystemClock.elapsedRealtime()
        var lastBytes = 0L
        var peak = 0.0
        var smoothed = 0.0
        var hasSmoothed = false
        var lastSampleEmitMs = 0L

        while (SystemClock.elapsedRealtime() < deadlineMs) {
            ensureNetworkAvailable()

            val nowMs = SystemClock.elapsedRealtime()
            val elapsedMs = nowMs - startMs
            val totalBytes = bytesCounter.get()

            val deltaBytes = totalBytes - lastBytes
            val deltaSeconds = ((nowMs - lastTickMs).toDouble() / 1000.0)
                .coerceIn(minSampleSeconds, 1.0)

            val rawMBps = megabytesPerSecond(deltaBytes, deltaSeconds)
            val alpha = (1.0 - exp(-deltaSeconds / smoothingTimeConstantSec.coerceAtLeast(0.2))).coerceIn(0.05, 0.7)
            smoothed = if (!hasSmoothed) {
                hasSmoothed = true
                rawMBps
            } else {
                smoothed + alpha * (rawMBps - smoothed)
            }

            val currentMBps = smoothed.takeIf { it.isFiniteValue() }?.coerceAtLeast(0.0) ?: 0.0
            val avgSeconds = max(0.001, elapsedMs.toDouble() / 1000.0)
            val avgMBps = megabytesPerSecond(totalBytes, avgSeconds).takeIf { it.isFiniteValue() }?.coerceAtLeast(0.0) ?: 0.0

            peak = max(peak, currentMBps)

            val shouldEmitSample = lastSampleEmitMs == 0L || (nowMs - lastSampleEmitMs) >= 500L
            val sample = if (shouldEmitSample && currentMBps.isFiniteValue()) {
                SpeedSample(elapsedMs = elapsedMs.coerceAtLeast(0L), mBps = currentMBps)
            } else {
                null
            }

            val phaseProgress = (elapsedMs.toDouble() / phaseDurationMs.toDouble()).toFloat().coerceIn(0f, 1f)
            val totalElapsed = nowMs - runStartElapsedMs.get()
            val totalProgress = if (plannedTotalMs > 0L) {
                (totalElapsed.toDouble() / plannedTotalMs.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            _state.value = when (updateDirection) {
                Direction.Download -> {
                    val existing = _state.value
                    existing.copy(
                        stage = stage,
                        elapsedMs = elapsedMs,
                        totalDurationMs = nowMs - runStartElapsedMs.get(),
                        plannedTotalDurationMs = plannedTotalMs,
                        phaseProgress = phaseProgress,
                        totalProgress = totalProgress,
                        download = existing.download.copy(
                            bytes = totalBytes,
                            currentMBps = currentMBps,
                            avgMBps = avgMBps,
                            peakMBps = max(existing.download.peakMBps, peak),
                        ),
                        downloadSamples = if (sample != null) {
                            (existing.downloadSamples + sample).takeLast(MAX_SAMPLES)
                        } else {
                            existing.downloadSamples
                        },
                        totalBytesUsed = totalBytes + existing.upload.bytes,
                    )
                }

                Direction.Upload -> {
                    val existing = _state.value
                    existing.copy(
                        stage = stage,
                        elapsedMs = elapsedMs,
                        totalDurationMs = nowMs - runStartElapsedMs.get(),
                        plannedTotalDurationMs = plannedTotalMs,
                        phaseProgress = phaseProgress,
                        totalProgress = totalProgress,
                        upload = existing.upload.copy(
                            bytes = totalBytes,
                            currentMBps = currentMBps,
                            avgMBps = avgMBps,
                            peakMBps = max(existing.upload.peakMBps, peak),
                        ),
                        uploadSamples = if (sample != null) {
                            (existing.uploadSamples + sample).takeLast(MAX_SAMPLES)
                        } else {
                            existing.uploadSamples
                        },
                        totalBytesUsed = existing.download.bytes + totalBytes,
                    )
                }
            }

            if (shouldEmitSample) {
                lastSampleEmitMs = nowMs
            }

            lastBytes = totalBytes
            lastTickMs = nowMs

            delay(updateIntervalMs.coerceIn(250L, 500L))
        }
    }

    private suspend fun downloadWorker(
        url: String,
        deadlineMs: Long,
        bytesCounter: AtomicLong,
    ) {
        withContext(ioDispatcher) {
            val buffer = ByteArray(64 * 1024)

            while (isActive && SystemClock.elapsedRealtime() < deadlineMs) {
                ensureNetworkAvailable()

                val conn = openConnection(url).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept-Encoding", "identity")
                }

                try {
                    conn.inputStream.use { input ->
                        while (isActive && SystemClock.elapsedRealtime() < deadlineMs) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            bytesCounter.addAndGet(read.toLong())
                        }
                    }
                } catch (ioe: IOException) {
                    if (SystemClock.elapsedRealtime() < deadlineMs) throw ioe
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private suspend fun uploadWorker(
        url: String,
        deadlineMs: Long,
        bytesCounter: AtomicLong,
        targetBytesPerRequest: Long,
    ) {
        withContext(ioDispatcher) {
            val buffer = ByteArray(64 * 1024)

            while (isActive && SystemClock.elapsedRealtime() < deadlineMs) {
                ensureNetworkAvailable()

                val conn = openConnection(url).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("Accept-Encoding", "identity")
                    setChunkedStreamingMode(64 * 1024)
                }

                try {
                    conn.outputStream.use { out ->
                        var remaining = targetBytesPerRequest
                        while (remaining > 0L && isActive && SystemClock.elapsedRealtime() < deadlineMs) {
                            val toWrite = min(remaining, buffer.size.toLong()).toInt()
                            out.write(buffer, 0, toWrite)
                            remaining -= toWrite.toLong()
                            bytesCounter.addAndGet(toWrite.toLong())
                        }
                        out.flush()
                    }

                    drainResponse(conn)
                } catch (ioe: IOException) {
                    if (SystemClock.elapsedRealtime() < deadlineMs) throw ioe
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.useCaches = false
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("Pragma", "no-cache")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn
    }

    private fun drainResponse(conn: HttpURLConnection) {
        val stream = try {
            conn.inputStream
        } catch (_: Throwable) {
            conn.errorStream
        } ?: return

        stream.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
            }
        }
    }

    private fun buildDownloadUrl(config: SpeedTestConfig): String {
        val sep = if (config.downloadUrl.contains("?")) "&" else "?"
        return "${config.downloadUrl}${sep}bytes=${config.downloadBytesPerRequest}"
    }

    private fun ensureNetworkAvailable() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: throw IOException("No active network")
        val caps = cm.getNetworkCapabilities(network)
            ?: throw IOException("No network capabilities")
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            throw IOException("Network has no internet capability")
        }
    }

    private fun megabytesPerSecond(bytesTransferred: Long, elapsedSeconds: Double): Double {
        val safeSeconds = elapsedSeconds.takeIf { it.isFiniteValue() } ?: return 0.0
        if (safeSeconds <= 0.0) return 0.0

        val mb = (bytesTransferred.toDouble() / BYTES_PER_MB)
        val value = mb / safeSeconds
        return if (value.isFiniteValue() && abs(value) < 1e9) value else 0.0
    }

    private enum class Direction {
        Download,
        Upload,
    }
}

enum class SpeedTestStage {
    Idle,
    Countdown,
    Downloading,
    PreparingUpload,
    Uploading,
    Completed,
    Error,
}

data class SpeedTestConfig(
    val downloadUrl: String = "https://speed.cloudflare.com/__down",
    val uploadUrl: String = "https://speed.cloudflare.com/__up",
    val countdownSeconds: Int = 3,
    val downloadDurationMs: Long = 12_000L,
    val uploadDurationMs: Long = 12_000L,
    val intermissionMs: Long = 600L,
    val updateIntervalMs: Long = 250L,
    val smoothingTimeConstantSec: Double = 1.0,
    val minSampleSeconds: Double = 0.08,
    val parallelDownloads: Int = 3,
    val parallelUploads: Int = 2,
    val downloadBytesPerRequest: Long = 100L * 1024L * 1024L,
    val uploadBytesPerRequest: Long = 5L * 1024L * 1024L,
)

data class SpeedTestState(
    val stage: SpeedTestStage = SpeedTestStage.Idle,
    val elapsedMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val plannedTotalDurationMs: Long = 0L,
    val countdownRemainingSec: Int = 0,
    val phaseProgress: Float = 0f,
    val totalProgress: Float = 0f,
    val download: DirectionStats = DirectionStats(),
    val upload: DirectionStats = DirectionStats(),
    val totalBytesUsed: Long = 0L,
    val downloadSamples: List<SpeedSample> = emptyList(),
    val uploadSamples: List<SpeedSample> = emptyList(),
    val message: String? = null,
)

data class DirectionStats(
    val currentMBps: Double = 0.0,
    val avgMBps: Double = 0.0,
    val peakMBps: Double = 0.0,
    val bytes: Long = 0L,
)

data class SpeedSample(
    val elapsedMs: Long,
    val mBps: Double,
)
