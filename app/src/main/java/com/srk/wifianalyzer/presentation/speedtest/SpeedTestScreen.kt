package com.srk.wifianalyzer.presentation.speedtest

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srk.wifianalyzer.R
import com.srk.wifianalyzer.data.speedtest.DirectionStats
import com.srk.wifianalyzer.data.speedtest.SpeedTestStage
import com.srk.wifianalyzer.data.speedtest.SpeedSample
import com.srk.wifianalyzer.presentation.components.AppScaffold
import com.srk.wifianalyzer.presentation.components.MessageCard
import com.srk.wifianalyzer.presentation.components.Spacing
import com.srk.wifianalyzer.settings.domain.models.SpeedTestGraphStyle
import com.srk.wifianalyzer.settings.domain.models.SpeedTestMode
import com.srk.wifianalyzer.settings.domain.models.SpeedUnit
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SpeedTestScreen(
    viewModel: SpeedTestViewModel = hiltViewModel(),
) {
    val speedUnit by viewModel.speedUnit.collectAsState()
    val testMode by viewModel.testMode.collectAsState()
    val graphStyle by viewModel.graphStyle.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onNotificationPermissionResult(granted)
        },
    )

    LaunchedEffect(Unit) {
        viewModel.refreshNotificationPermission()
        viewModel.effects.collect { effect ->
            when (effect) {
                SpeedTestViewModel.Effect.RequestPostNotificationsPermission -> {
                    if (Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    AppScaffold(
        title = stringResource(R.string.internet_speed_title),
        titleIcon = Icons.Filled.Speed,
    ) { contentModifier ->
        val scrollState = rememberScrollState()

        Column(
            modifier = contentModifier
                .padding(Spacing.s16)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            NotificationPermissionCard(viewModel = viewModel)
            ErrorCard(viewModel = viewModel)
            StatusCard(viewModel = viewModel)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            ) {
                when (testMode) {
                    SpeedTestMode.DownloadOnly -> {
                        SpeedMetricCard(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(R.string.download),
                            icon = Icons.Filled.Download,
                            accent = MaterialTheme.colorScheme.primary,
                            statsFlow = viewModel.downloadStats,
                            samplesFlow = viewModel.downloadSamples,
                            speedUnit = speedUnit,
                            graphStyle = graphStyle,
                        )
                    }

                    SpeedTestMode.UploadOnly -> {
                        SpeedMetricCard(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(R.string.upload),
                            icon = Icons.Filled.Upload,
                            accent = MaterialTheme.colorScheme.tertiary,
                            statsFlow = viewModel.uploadStats,
                            samplesFlow = viewModel.uploadSamples,
                            speedUnit = speedUnit,
                            graphStyle = graphStyle,
                        )
                    }

                    SpeedTestMode.Full -> {
                        SpeedMetricCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.download),
                            icon = Icons.Filled.Download,
                            accent = MaterialTheme.colorScheme.primary,
                            statsFlow = viewModel.downloadStats,
                            samplesFlow = viewModel.downloadSamples,
                            speedUnit = speedUnit,
                            graphStyle = graphStyle,
                        )

                        SpeedMetricCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.upload),
                            icon = Icons.Filled.Upload,
                            accent = MaterialTheme.colorScheme.tertiary,
                            statsFlow = viewModel.uploadStats,
                            samplesFlow = viewModel.uploadSamples,
                            speedUnit = speedUnit,
                            graphStyle = graphStyle,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun NotificationPermissionCard(
    viewModel: SpeedTestViewModel,
) {
    val hasPermission by viewModel.hasNotificationPermission.collectAsState()
    if (Build.VERSION.SDK_INT < 33 || hasPermission) return

    MessageCard(
        title = stringResource(R.string.notification_permission_required_title),
        message = stringResource(R.string.notification_permission_required_message),
        actionText = stringResource(R.string.grant_permission),
        onAction = { viewModel.onRequestNotificationPermission() },
    )
}

@Composable
private fun ErrorCard(
    viewModel: SpeedTestViewModel,
) {
    val message by viewModel.message.collectAsState()
    if (message == null) return

    MessageCard(
        title = stringResource(R.string.speed_test_error_title),
        message = message.orEmpty(),
        actionText = if (Build.VERSION.SDK_INT >= 26) stringResource(R.string.open_network_settings) else null,
        onAction = { viewModel.onOpenNetworkSettingsClicked() },
    )
}

@Composable
private fun StatusCard(
    viewModel: SpeedTestViewModel,
) {
    val status by viewModel.status.collectAsState()
    val canStart by viewModel.canStart.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s16),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Text(
                        text = stageLabel(status.stage, status.countdownRemainingSec),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = stringResource(R.string.wifi_required_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (status.plannedTotalDurationMs > 0L) {
                val totalPct = (status.totalProgress.coerceIn(0f, 1f) * 100f).roundToInt()
                val phasePct = (status.phaseProgress.coerceIn(0f, 1f) * 100f).roundToInt()
                val totalProgressA11y = stringResource(R.string.a11y_total_progress_percent, totalPct)
                val phaseProgressA11y = stringResource(R.string.a11y_phase_progress_percent, phasePct)

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    LinearProgressIndicator(
                        progress = { status.totalProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = totalProgressA11y },
                    )
                    LinearProgressIndicator(
                        progress = { status.phaseProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = phaseProgressA11y },
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            ) {
                MetricTiny(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.duration_label),
                    value = "${String.format(Locale.US, "%.1f", status.totalDurationMs.toDouble() / 1000.0)} s",
                )
                MetricTiny(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.data_used_label),
                    value = "${formatMegabytes(status.totalBytesUsed)} MB",
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                FilledTonalButton(
                    onClick = { viewModel.onStartClicked() },
                    enabled = canStart,
                    modifier = Modifier.defaultMinSize(minWidth = 120.dp),
                ) {
                    Icon(
                        imageVector = if (status.stage == SpeedTestStage.Completed) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (status.stage == SpeedTestStage.Completed) stringResource(R.string.run_again) else stringResource(R.string.start))
                }

                OutlinedButton(
                    onClick = { viewModel.onStopClicked() },
                    enabled = isRunning,
                    modifier = Modifier.defaultMinSize(minWidth = 120.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.stop))
                }
            }
        }
    }
}

@Composable
private fun SpeedMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    accent: Color,
    statsFlow: StateFlow<DirectionStats>,
    samplesFlow: StateFlow<List<SpeedSample>>,
    speedUnit: SpeedUnit,
    graphStyle: SpeedTestGraphStyle,
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s16),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accent.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.s8))
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.live_speed_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = if (speedUnit == SpeedUnit.Mbps) stringResource(R.string.unit_mbps) else stringResource(R.string.unit_mb_per_s),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SpeedMetricHeader(
                statsFlow = statsFlow,
                speedUnit = speedUnit,
            )

            SpeedMetricChart(
                accentColor = accent,
                samplesFlow = samplesFlow,
                speedUnit = speedUnit,
                showFill = graphStyle == SpeedTestGraphStyle.Filled,
            )
        }
    }
}

@Composable
private fun SpeedMetricHeader(
    statsFlow: StateFlow<DirectionStats>,
    speedUnit: SpeedUnit,
) {
    val stats by statsFlow.collectAsState()

    val multiplier = if (speedUnit == SpeedUnit.Mbps) 8.0 else 1.0

    val animated = animateFloatAsState(
        targetValue = (stats.currentMBps * multiplier).toFloat(),
        animationSpec = tween(durationMillis = 350),
        label = "speed",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val speedText = formatMBps(animated.value.toDouble())
        val a11yText = if (speedUnit == SpeedUnit.Mbps) {
            stringResource(R.string.a11y_speed_value_mbps, speedText)
        } else {
            stringResource(R.string.a11y_speed_value_mb_per_s, speedText)
        }

        val avgValueText = formatMBps(stats.avgMBps * multiplier)
        val peakValueText = formatMBps(stats.peakMBps * multiplier)
        val avgA11yText = if (speedUnit == SpeedUnit.Mbps) {
            stringResource(R.string.a11y_average_speed_value_mbps, avgValueText)
        } else {
            stringResource(R.string.a11y_average_speed_value_mb_per_s, avgValueText)
        }
        val peakA11yText = if (speedUnit == SpeedUnit.Mbps) {
            stringResource(R.string.a11y_peak_speed_value_mbps, peakValueText)
        } else {
            stringResource(R.string.a11y_peak_speed_value_mb_per_s, peakValueText)
        }

        Text(
            text = speedText,
            modifier = Modifier.semantics {
                contentDescription = a11yText
            },
            style = MaterialTheme.typography.displaySmall,
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${stringResource(R.string.avg_short)} $avgValueText",
                modifier = Modifier.semantics { contentDescription = avgA11yText },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${stringResource(R.string.peak_short)} $peakValueText",
                modifier = Modifier.semantics { contentDescription = peakA11yText },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpeedMetricChart(
    accentColor: Color,
    samplesFlow: StateFlow<List<SpeedSample>>,
    speedUnit: SpeedUnit,
    showFill: Boolean,
) {
    val samples by samplesFlow.collectAsState()

    val multiplier = if (speedUnit == SpeedUnit.Mbps) 8.0 else 1.0
    val displaySamples = remember(samples, speedUnit) {
        if (multiplier == 1.0) {
            samples
        } else {
            samples.map { it.copy(mBps = it.mBps * multiplier) }
        }
    }

    SpeedLineChart(
        modifier = Modifier
            .height(160.dp)
            .fillMaxWidth(),
        samples = displaySamples,
        accentColor = accentColor,
        showFill = showFill,
    )
}

@Composable
private fun MetricTiny(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s12, vertical = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun stageLabel(stage: SpeedTestStage, countdownRemainingSec: Int): String {
    return when (stage) {
        SpeedTestStage.Idle -> stringResource(R.string.stage_ready)
        SpeedTestStage.Countdown -> stringResource(R.string.stage_starting_in, countdownRemainingSec)
        SpeedTestStage.Downloading -> stringResource(R.string.stage_testing_download)
        SpeedTestStage.PreparingUpload -> stringResource(R.string.stage_preparing_upload)
        SpeedTestStage.Uploading -> stringResource(R.string.stage_testing_upload)
        SpeedTestStage.Completed -> stringResource(R.string.stage_completed)
        SpeedTestStage.Error -> stringResource(R.string.stage_error)
    }
}

private fun formatMBps(value: Double): String {
    return String.format(Locale.US, "%.2f", value)
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / 1_000_000.0
    return String.format(Locale.US, "%.1f", mb)
}
