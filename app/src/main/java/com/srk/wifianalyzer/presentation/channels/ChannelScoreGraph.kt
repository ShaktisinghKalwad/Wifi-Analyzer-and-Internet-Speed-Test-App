package com.srk.wifianalyzer.presentation.channels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.srk.wifianalyzer.domain.analysis.ChannelAnalysis
import com.srk.wifianalyzer.ui.LocalGraphDensity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ChannelScoreGraph(
    modifier: Modifier,
    analysis: ChannelAnalysis,
) {
    val graphDensity = LocalGraphDensity.current
    val lineStrokeWidth = 6f * graphDensity
    val bestRadius = 10f * graphDensity
    val selectedRadius = 12f * graphDensity

    val lineColor = MaterialTheme.colorScheme.primary
    val bestColor = MaterialTheme.colorScheme.tertiary
    val selectedColor = MaterialTheme.colorScheme.secondary

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPoint by remember { mutableStateOf<Offset?>(null) }

    val channels = analysis.channels
    if (channels.isEmpty()) return

    val firstChannel = channels.first().channel
    val midChannel = channels[channels.size / 2].channel
    val lastChannel = channels.last().channel

    fun xFor(index: Int, widthPx: Float): Float {
        return (index.toFloat() / (channels.size - 1).coerceAtLeast(1)) * widthPx
    }

    val maxScore = channels.maxOf { it.score }
    val minScore = channels.minOf { it.score }
    val range = max(1e-9, maxScore - minScore)

    fun yFor(score: Double, heightPx: Float): Float {
        val norm = ((score - minScore) / range).toFloat()
        return (1f - norm) * heightPx
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(channels) {
                    detectTapGestures { tap ->
                        if (channels.isEmpty()) return@detectTapGestures

                        val widthPx = size.width.toFloat()
                        val heightPx = size.height.toFloat()

                        var bestIdx = 0
                        var bestDx = Float.MAX_VALUE
                        for (idx in channels.indices) {
                            val x = xFor(idx, widthPx)
                            val dx = abs(tap.x - x)
                            if (dx < bestDx) {
                                bestDx = dx
                                bestIdx = idx
                            }
                        }

                        val togglingSame = selectedIndex == bestIdx
                        if (togglingSame) {
                            selectedIndex = null
                            selectedPoint = null
                        } else {
                            selectedIndex = bestIdx
                            val score = channels[bestIdx].score
                            selectedPoint = Offset(
                                xFor(bestIdx, widthPx),
                                yFor(score, heightPx),
                            )
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val widthPx = size.width
                val heightPx = size.height

                val path = Path()
                channels.forEachIndexed { idx, s ->
                    val x = xFor(idx, widthPx)
                    val y = yFor(s.score, heightPx)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round),
                )

                val bestSet = analysis.bestChannels.toSet()
                channels.forEachIndexed { idx, s ->
                    if (s.channel in bestSet) {
                        drawCircle(
                            color = bestColor,
                            radius = bestRadius,
                            center = Offset(xFor(idx, widthPx), yFor(s.score, heightPx)),
                        )
                    }
                }

                val selIdx = selectedIndex
                if (selIdx != null && selIdx in channels.indices) {
                    val s = channels[selIdx]
                    drawCircle(
                        color = selectedColor,
                        radius = selectedRadius,
                        center = Offset(xFor(selIdx, widthPx), yFor(s.score, heightPx)),
                    )
                }
            }

            selectedIndex?.let { idx ->
                val s = channels.getOrNull(idx) ?: return@let
                val point = selectedPoint ?: return@let

                val label = "Ch ${s.channel} • ${String.format(Locale.US, "%.2e", s.score)}"
                val x = (point.x - 60f).roundToInt().coerceAtLeast(0)
                val y = (point.y - 44f).roundToInt().coerceAtLeast(0)

                Text(
                    text = label,
                    modifier = Modifier
                        .padding(0.dp)
                        .offset { androidx.compose.ui.unit.IntOffset(x, y) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
