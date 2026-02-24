package com.srk.wifianalyzer.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srk.wifianalyzer.domain.model.WifiAccessPoint
import com.srk.wifianalyzer.domain.model.WifiBand
import com.srk.wifianalyzer.domain.model.WifiChannelWidth
import com.srk.wifianalyzer.presentation.components.Spacing
import com.srk.wifianalyzer.settings.domain.models.SignalUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AccessPointCard(
    ap: WifiAccessPoint,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    showBssid: Boolean = true,
    signalUnit: SignalUnit = SignalUnit.Dbm,
    onClick: (() -> Unit)? = null,
) {
    val padding = if (dense) Spacing.s8 else Spacing.s12
    val barHeight = if (dense) 34.dp else 40.dp
    val chipScrollState = rememberScrollState()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (dense) 76.dp else 84.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12)
        ) {
            SignalBars(
                modifier = Modifier
                    .height(barHeight)
                    .width(24.dp),
                rssiDbm = ap.rssiDbm,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (ap.ssid.isBlank()) "(Hidden SSID)" else ap.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s8))
                    Text(
                        text = formatSignal(ap.rssiDbm, signalUnit),
                        style = MaterialTheme.typography.titleSmall,
                        color = rssiColor(ap.rssiDbm),
                    )
                }

                val widthText = when (ap.channelWidth) {
                    WifiChannelWidth.Unknown -> null
                    WifiChannelWidth.W80P80 -> "80+80"
                    else -> "${ap.channelWidth.mhz}"
                }
                val subtitleParts = buildList {
                    add(formatFrequencyGHz(ap.frequencyMhz))
                    add("ch ${ap.channel ?: "?"}")
                    if (widthText != null) add("${widthText}MHz")
                }
                Text(
                    text = subtitleParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier
                        .padding(top = Spacing.s8)
                        .horizontalScroll(chipScrollState),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(text = bandLabel(ap.band)) },
                    )
                    AssistChip(onClick = {}, enabled = false, label = { Text(text = ap.security.label) })
                    AssistChip(onClick = {}, enabled = false, label = { Text(text = ap.standard.label) })
                    if (widthText != null) {
                        AssistChip(onClick = {}, enabled = false, label = { Text(text = "${widthText}MHz") })
                    }
                }

                if (showBssid) {
                    Text(
                        text = ap.bssid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalBars(
    modifier: Modifier,
    rssiDbm: Int,
) {
    val activeBars = when {
        rssiDbm >= -60 -> 4
        rssiDbm >= -70 -> 3
        rssiDbm >= -80 -> 2
        rssiDbm >= -90 -> 1
        else -> 0
    }
    val activeColor = rssiColor(rssiDbm)
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val gapPx = (size.width * 0.12f).coerceAtLeast(1f)
        val barWidth = (size.width - gapPx * 3) / 4f
        for (i in 0 until 4) {
            val level = i + 1
            val barHeight = size.height * (level / 4f)
            val x = i * (barWidth + gapPx)
            val y = size.height - barHeight
            drawRoundRect(
                color = if (level <= activeBars) activeColor else inactiveColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(x = barWidth / 2f, y = barWidth / 2f),
            )
        }
    }
}

@Composable
private fun rssiColor(rssiDbm: Int): Color {
    return when {
        rssiDbm >= -60 -> MaterialTheme.colorScheme.tertiary
        rssiDbm >= -70 -> MaterialTheme.colorScheme.primary
        rssiDbm >= -80 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
}

private fun bandLabel(band: WifiBand): String {
    return when (band) {
        WifiBand.Band2G -> "2.4G"
        WifiBand.Band5G -> "5G"
        WifiBand.Band6G -> "6G"
        WifiBand.Unknown -> "?"
    }
}

private fun formatFrequencyGHz(frequencyMhz: Int): String {
    val ghz = frequencyMhz.toFloat() / 1000f
    return String.format(Locale.US, "%.1f GHz", ghz)
}

private fun formatSignal(rssiDbm: Int, unit: SignalUnit): String {
    return when (unit) {
        SignalUnit.Dbm -> "$rssiDbm dBm"
        SignalUnit.Percentage -> "${rssiToPercent(rssiDbm)}%"
    }
}

private fun rssiToPercent(rssiDbm: Int): Int {
    val minDbm = -100
    val maxDbm = -50
    val clamped = rssiDbm.coerceIn(minDbm, maxDbm)
    return (((clamped - minDbm).toFloat() / (maxDbm - minDbm).toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
}
