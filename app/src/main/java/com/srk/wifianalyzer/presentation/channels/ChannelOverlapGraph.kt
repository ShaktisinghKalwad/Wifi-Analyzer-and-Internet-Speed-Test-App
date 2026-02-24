package com.srk.wifianalyzer.presentation.channels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.srk.wifianalyzer.domain.analysis.ChannelAnalysis
import com.srk.wifianalyzer.ui.LocalGraphDensity
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

@Composable
fun ChannelOverlapGraph(
    modifier: Modifier,
    analysis: ChannelAnalysis,
    recommendationWidthMhz: Int,
) {
    val graphDensity = LocalGraphDensity.current

    val channels = analysis.channels
    if (channels.isEmpty()) return

    val aps = analysis.observedAps
    if (aps.isEmpty()) return

    val firstChannel = channels.first().channel
    val midChannel = channels[channels.size / 2].channel
    val lastChannel = channels.last().channel

    val lineColor = MaterialTheme.colorScheme.primary
    val bestColor = MaterialTheme.colorScheme.tertiary
    val sumLineColor = MaterialTheme.colorScheme.onSurface

    val maxPower = aps.maxOf { dbmToLinear(it.rssiDbm) }.coerceAtLeast(1e-12)

    fun xFor(index: Int, widthPx: Float): Float {
        return (index.toFloat() / (channels.size - 1).coerceAtLeast(1)) * widthPx
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val widthPx = size.width
            val heightPx = size.height

            val summed = DoubleArray(channels.size)

            aps.forEachIndexed { apIdx, ap ->
                val powerNorm = (dbmToLinear(ap.rssiDbm) / maxPower).coerceIn(0.0, 1.0)
                val effectiveWidth = max(ap.widthMhz, recommendationWidthMhz)
                val sigma = sigmaChannels(effectiveWidth)

                val path = Path()
                for (idx in channels.indices) {
                    val ch = channels[idx].channel.toDouble()
                    val overlap = gaussianOverlap(ch, ap.channel.toDouble(), sigma)
                    val value = overlap * powerNorm
                    summed[idx] += value

                    val x = xFor(idx, widthPx)
                    val y = (1f - value.toFloat().coerceIn(0f, 1f)) * heightPx
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                val alpha = (0.15f + 0.65f * powerNorm.toFloat()).coerceIn(0.15f, 0.8f)
                val color = lineColor.copy(alpha = alpha)
                val strokeWidth = ((2f + 2f * powerNorm.toFloat()) * graphDensity).coerceIn(2f, 8f)

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            val sumMax = summed.maxOrNull()?.coerceAtLeast(1e-9) ?: 1e-9
            val sumPath = Path()
            for (idx in channels.indices) {
                val value = (summed[idx] / sumMax).coerceIn(0.0, 1.0)
                val x = xFor(idx, widthPx)
                val y = (1f - value.toFloat()) * heightPx
                if (idx == 0) sumPath.moveTo(x, y) else sumPath.lineTo(x, y)
            }

            drawPath(
                path = sumPath,
                color = sumLineColor,
                style = Stroke(width = (5f * graphDensity).coerceIn(3f, 10f), cap = StrokeCap.Round),
            )

            val bestSet = analysis.bestChannels.toSet()
            channels.forEachIndexed { idx, s ->
                if (s.channel in bestSet) {
                    val x = xFor(idx, widthPx)
                    val y = 12f
                    drawCircle(
                        color = bestColor,
                        radius = 9f * graphDensity,
                        center = Offset(x, y),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Text(
                text = "Ch $firstChannel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Ch $midChannel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Ch $lastChannel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun sigmaChannels(widthMhz: Int): Double {
    return when (widthMhz) {
        20 -> 2.0
        40 -> 4.0
        80 -> 8.0
        160 -> 16.0
        else -> 2.0
    }
}

private fun gaussianOverlap(x: Double, mu: Double, sigma: Double): Double {
    val z = (x - mu) / sigma
    return exp(-0.5 * z * z)
}

private fun dbmToLinear(dbm: Int): Double {
    return 10.0.pow(dbm / 10.0)
}
