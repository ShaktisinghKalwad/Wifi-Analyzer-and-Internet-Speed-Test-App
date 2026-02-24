package com.srk.wifianalyzer.presentation.apdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.srk.wifianalyzer.presentation.components.AccessPointCard
import com.srk.wifianalyzer.presentation.components.AppScaffold
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AccessPointDetailsScreen(
    onBack: () -> Unit,
    onOpenApDetails: (String) -> Unit = {},
    viewModel: AccessPointDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val ap = uiState.accessPoint

    var smooth by remember { mutableStateOf(true) }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    AppScaffold(
        title = ap?.ssid?.ifBlank { "(Hidden SSID)" } ?: "Access Point",
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            ElevatedCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (ap == null) {
                        Text(
                            text = "Not currently visible in the latest scan (showing session history)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = buildApHeaderLine(ap = ap),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Text(
                        text = uiState.bssid,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    StatusAndTiming(
                        modifier = Modifier.padding(top = 12.dp),
                        samples = uiState.samples,
                        nowMs = nowMs,
                    )

                    SmoothingAndStats(
                        modifier = Modifier.padding(top = 8.dp),
                        samples = uiState.samples,
                        smooth = smooth,
                        onSmoothChange = { smooth = it },
                    )
                }
            }

            RssiLineChart(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(180.dp)
                    .fillMaxWidth(),
                samples = uiState.samples,
                smooth = smooth,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    val last = uiState.samples.lastOrNull()
                    if (last != null) {
                        Text(
                            text = "Last sample: ${last.rssiDbm} dBm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (uiState.similarAccessPoints.isNotEmpty()) {
                    item {
                        Text(
                            text = "Similar APs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(
                        items = uiState.similarAccessPoints,
                        key = { it.bssid }
                    ) { other ->
                        AccessPointCard(
                            ap = other,
                            dense = true,
                            onClick = { onOpenApDetails(other.bssid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmoothingAndStats(
    modifier: Modifier,
    samples: List<RssiSample>,
    smooth: Boolean,
    onSmoothChange: (Boolean) -> Unit,
) {
    val stats = remember(samples) { computeRssiWindowStats(samples) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Smoothing",
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = smooth,
                onCheckedChange = onSmoothChange,
            )
        }

        Text(
            text = stats?.let {
                "Window: ${it.count} samples • Min ${it.minDbm} dBm • Max ${it.maxDbm} dBm • Avg ${String.format("%.1f", it.avgDbm)} dBm"
            } ?: "Window: —",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusAndTiming(
    modifier: Modifier,
    samples: List<RssiSample>,
    nowMs: Long,
) {
    val last = samples.lastOrNull()
    val avgIntervalMs = averageIntervalMs(samples)
    val assumedIntervalMs = avgIntervalMs ?: 30_000L
    val seenThresholdMs = max(15_000L, (assumedIntervalMs * 2.2).toLong())
    val ageMs = if (last != null) max(0L, nowMs - last.timestampMs) else null
    val isSeen = ageMs != null && ageMs <= seenThresholdMs

    val statusColor = if (isSeen) Color(0xFF00C853) else MaterialTheme.colorScheme.error
    val statusText = if (isSeen) "Seen" else "Not seen"

    Column(modifier = modifier) {
        Row {
            Text(
                text = "Status: ",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
            )
        }

        Text(
            text = "Last seen: ${ageMs?.let { formatAge(it) } ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val intervalText = avgIntervalMs?.let { formatInterval(it) } ?: "—"
        val ratePerMin = avgIntervalMs?.let { 60_000.0 / max(1.0, it.toDouble()) }
        val rateText = ratePerMin?.let { String.format("%.1f/min", it) } ?: "—"

        Text(
            text = "Sample rate: $rateText • Avg interval: $intervalText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun averageIntervalMs(samples: List<RssiSample>): Long? {
    if (samples.size < 3) return null

    val allDeltas: List<Long> = samples
        .zipWithNext { a, b -> b.timestampMs - a.timestampMs }
        .filter { it in 1..5 * 60 * 1000L }

    val deltas: List<Long> = if (allDeltas.size > 12) allDeltas.takeLast(12) else allDeltas

    if (deltas.isEmpty()) return null
    return deltas.sum() / deltas.size
}

private fun formatAge(ms: Long): String {
    val s = (ms / 1000).toInt()
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ${s % 60}s ago"
        else -> "${s / 3600}h ${(s % 3600) / 60}m ago"
    }
}

private fun formatInterval(ms: Long): String {
    val clamped = min(ms, 10 * 60 * 1000L)
    val sec = clamped / 1000.0
    return String.format("%.1fs", sec)
}

private data class RssiWindowStats(
    val count: Int,
    val minDbm: Int,
    val maxDbm: Int,
    val avgDbm: Double,
)

private fun computeRssiWindowStats(samples: List<RssiSample>): RssiWindowStats? {
    if (samples.isEmpty()) return null
    val minDbm = samples.minOf { it.rssiDbm }
    val maxDbm = samples.maxOf { it.rssiDbm }
    val avgDbm = samples.sumOf { it.rssiDbm }.toDouble() / samples.size.toDouble()
    return RssiWindowStats(
        count = samples.size,
        minDbm = minDbm,
        maxDbm = maxDbm,
        avgDbm = avgDbm,
    )
}

private fun buildApHeaderLine(ap: com.srk.wifianalyzer.domain.model.WifiAccessPoint): String {
    val primaryCh = ap.channel?.toString() ?: "?"
    val primary = "${ap.frequencyMhz} MHz (ch $primaryCh)"

    val center0 = ap.centerFreq0Mhz?.let { cf ->
        val ch = freqMhzToChannel(cf)?.toString() ?: "?"
        "$cf MHz (ch $ch)"
    }
    val center1 = ap.centerFreq1Mhz?.let { cf ->
        val ch = freqMhzToChannel(cf)?.toString() ?: "?"
        "$cf MHz (ch $ch)"
    }

    val center = when {
        center0 == null && center1 == null -> null
        center0 != null && center1 == null -> center0
        center0 == null && center1 != null -> center1
        else -> "${center0} + ${center1}"
    }

    val widthText = when (ap.channelWidth) {
        com.srk.wifianalyzer.domain.model.WifiChannelWidth.W80P80 -> "80+80 MHz"
        com.srk.wifianalyzer.domain.model.WifiChannelWidth.Unknown -> "—"
        else -> "${ap.channelWidth.mhz} MHz"
    }

    val channelParts = buildString {
        append(primary)
        if (center != null && center != primary) {
            append(" • center ")
            append(center)
        }
        append(" • width ")
        append(widthText)
    }

    return "${ap.rssiDbm} dBm • ${ap.security.label} • ${ap.standard.label} • $channelParts"
}

private fun freqMhzToChannel(freq: Int): Int? {
    if (freq == 2484) return 14
    if (freq in 2412..2472) return (freq - 2407) / 5
    if (freq in 5000..5900) return (freq - 5000) / 5
    if (freq == 5935) return 2
    if (freq in 5955..7115) return (freq - 5950) / 5
    return null
}
