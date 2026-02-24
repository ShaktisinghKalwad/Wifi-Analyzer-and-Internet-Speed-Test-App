package com.srk.wifianalyzer.presentation.speedtest

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.Immutable
import com.srk.wifianalyzer.data.speedtest.SpeedTestEngine
import com.srk.wifianalyzer.data.speedtest.SpeedTestStage
import com.srk.wifianalyzer.data.speedtest.DirectionStats
import com.srk.wifianalyzer.data.speedtest.SpeedSample
import com.srk.wifianalyzer.data.speedtest.SpeedTestConfig
import com.srk.wifianalyzer.service.SpeedTestForegroundService
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import com.srk.wifianalyzer.settings.domain.models.SpeedTestGraphStyle
import com.srk.wifianalyzer.settings.domain.models.SpeedTestMode
import com.srk.wifianalyzer.settings.domain.models.SpeedTestServerSelection
import com.srk.wifianalyzer.settings.domain.models.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val speedTestEngine: SpeedTestEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    sealed interface Effect {
        data object RequestPostNotificationsPermission : Effect
    }

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private val _hasNotificationPermission = MutableStateFlow(checkNotificationPermission())
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _pendingStart = MutableStateFlow(false)
    private val _startInFlight = MutableStateFlow(false)

    val speedUnit: StateFlow<SpeedUnit> = settingsRepository.userSettings
        .map { it.speedTest.speedUnit }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SpeedUnit.MBps,
        )

    val testMode: StateFlow<SpeedTestMode> = settingsRepository.userSettings
        .map { it.speedTest.mode }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SpeedTestMode.Full,
        )

    val graphStyle: StateFlow<SpeedTestGraphStyle> = settingsRepository.userSettings
        .map { it.speedTest.graphStyle }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SpeedTestGraphStyle.Line,
        )

    private val serverSelection: StateFlow<SpeedTestServerSelection> = settingsRepository.userSettings
        .map { it.speedTest.serverSelection }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SpeedTestServerSelection.AutoNearest,
        )

    private val manualServerUrl: StateFlow<String> = settingsRepository.userSettings
        .map { it.speedTest.manualServerUrl }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = "",
        )

    val stage: StateFlow<SpeedTestStage> = speedTestEngine.state
        .map { it.stage }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SpeedTestStage.Idle,
        )

    val message: StateFlow<String?> = speedTestEngine.state
        .map { it.message }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    val status: StateFlow<SpeedTestStatusUiState> = speedTestEngine.state
        .map { state ->
            SpeedTestStatusUiState(
                stage = state.stage,
                countdownRemainingSec = state.countdownRemainingSec,
                plannedTotalDurationMs = state.plannedTotalDurationMs,
                phaseProgress = state.phaseProgress,
                totalProgress = state.totalProgress,
                totalDurationMs = state.totalDurationMs,
                totalBytesUsed = state.totalBytesUsed,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SpeedTestStatusUiState(),
        )

    val downloadStats: StateFlow<DirectionStats> = speedTestEngine.state
        .map { it.download }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = DirectionStats(),
        )

    val uploadStats: StateFlow<DirectionStats> = speedTestEngine.state
        .map { it.upload }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = DirectionStats(),
        )

    val downloadSamples: StateFlow<List<SpeedSample>> = speedTestEngine.state
        .map { it.downloadSamples }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    val uploadSamples: StateFlow<List<SpeedSample>> = speedTestEngine.state
        .map { it.uploadSamples }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    val isRunning: StateFlow<Boolean> = stage
        .map { it == SpeedTestStage.Countdown || it == SpeedTestStage.Downloading || it == SpeedTestStage.PreparingUpload || it == SpeedTestStage.Uploading }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = false,
        )

    val canStart: StateFlow<Boolean> = stage
        .map { it == SpeedTestStage.Idle || it == SpeedTestStage.Completed || it == SpeedTestStage.Error }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = true,
        )

    init {
        viewModelScope.launch {
            stage.collect { s ->
                if (s == SpeedTestStage.Countdown || s == SpeedTestStage.Downloading || s == SpeedTestStage.PreparingUpload || s == SpeedTestStage.Uploading) {
                    _startInFlight.value = false
                    _pendingStart.value = false
                }
            }
        }
    }

    fun refreshNotificationPermission() {
        _hasNotificationPermission.value = checkNotificationPermission()
    }

    fun onRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (_hasNotificationPermission.value) return
        _effects.tryEmit(Effect.RequestPostNotificationsPermission)
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _hasNotificationPermission.value = granted

        if (granted && _pendingStart.value) {
            _pendingStart.value = false
            startInternal()
        } else {
            _pendingStart.value = false
        }
    }

    fun onStartClicked() {
        refreshNotificationPermission()
        if (!canStart.value) return

        startInternal()
    }

    fun onStopClicked() {
        _pendingStart.value = false
        _startInFlight.value = false

        SpeedTestForegroundService.stop(appContext)
        speedTestEngine.stop()
    }

    fun onOpenNetworkSettingsClicked() {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private fun startInternal() {
        if (!canStart.value) return
        if (_startInFlight.value) return

        _startInFlight.value = true
        val urls = deriveServerUrls(
            selection = serverSelection.value,
            manualUrl = manualServerUrl.value,
        )

        val base = SpeedTestConfig().let { cfg ->
            when {
                !urls.first.isNullOrBlank() && !urls.second.isNullOrBlank() -> cfg.copy(downloadUrl = urls.first!!, uploadUrl = urls.second!!)
                !urls.first.isNullOrBlank() -> cfg.copy(downloadUrl = urls.first!!)
                !urls.second.isNullOrBlank() -> cfg.copy(uploadUrl = urls.second!!)
                else -> cfg
            }
        }

        val config = when (testMode.value) {
            SpeedTestMode.Full -> base
            SpeedTestMode.DownloadOnly -> base.copy(uploadDurationMs = 0L, intermissionMs = 0L)
            SpeedTestMode.UploadOnly -> base.copy(downloadDurationMs = 0L, intermissionMs = 0L)
        }

        speedTestEngine.startFullTest(config)

        if (canUseForegroundService()) {
            runCatching {
                SpeedTestForegroundService.start(
                    appContext,
                    mode = testMode.value,
                    unit = speedUnit.value,
                    downloadUrl = urls.first,
                    uploadUrl = urls.second,
                )
            }
        }

        viewModelScope.launch {
            delay(1_000)
            _startInFlight.value = false
        }
    }

    private fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun canUseForegroundService(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return checkNotificationPermission()
    }

    private fun deriveServerUrls(
        selection: SpeedTestServerSelection,
        manualUrl: String,
    ): Pair<String?, String?> {
        if (selection != SpeedTestServerSelection.Manual) return null to null

        val raw = manualUrl.trim()
        if (raw.isBlank()) return null to null

        val normalized = raw.removeSuffix("/")

        return when {
            normalized.contains("__down") -> normalized to normalized.replace("__down", "__up")
            normalized.contains("__up") -> normalized.replace("__up", "__down") to normalized
            else -> "${normalized}/__down" to "${normalized}/__up"
        }
    }
}

@Immutable
data class SpeedTestStatusUiState(
    val stage: SpeedTestStage = SpeedTestStage.Idle,
    val countdownRemainingSec: Int = 0,
    val plannedTotalDurationMs: Long = 0L,
    val phaseProgress: Float = 0f,
    val totalProgress: Float = 0f,
    val totalDurationMs: Long = 0L,
    val totalBytesUsed: Long = 0L,
)
