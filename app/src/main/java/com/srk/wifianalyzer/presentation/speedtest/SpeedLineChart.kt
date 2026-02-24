package com.srk.wifianalyzer.presentation.speedtest

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import com.srk.wifianalyzer.data.speedtest.SpeedSample
import com.srk.wifianalyzer.ui.LocalGraphDensity
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun SpeedLineChart(
    modifier: Modifier,
    samples: List<SpeedSample>,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    showFill: Boolean = true,
) {
    val graphDensity = LocalGraphDensity.current
    val gridLines = (4f * graphDensity).roundToInt().coerceIn(2, 8)
    val gridStrokeWidth = 2f * graphDensity
    val lineStrokeWidth = 5f * graphDensity
    val glowRadius = 12f * graphDensity
    val dotRadius = 7f * graphDensity

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val lineColor = accentColor
    val fillColorTop = accentColor.copy(alpha = 0.22f)
    val fillColorBottom = accentColor.copy(alpha = 0.02f)

    fun isFiniteValue(v: Double): Boolean = !v.isNaN() && !v.isInfinite()
    val safeSamples = samples
        .asSequence()
        .filter { isFiniteValue(it.mBps) }
        .filter { it.mBps >= 0.0 }
        .toList()

    Box(
        modifier = modifier.drawWithCache {
            if (safeSamples.size < 2) {
                onDrawBehind { }
            } else {
                val minT = safeSamples.minOf { it.elapsedMs }
                val maxT = safeSamples.maxOf { it.elapsedMs }
                val tRange = max(1L, maxT - minT)

                val maxV = max(1e-6, safeSamples.maxOf { it.mBps })
                val paddedMax = maxV * 1.1

                fun xFor(t: Long): Float {
                    val norm = (t - minT).toFloat() / tRange.toFloat()
                    return norm * size.width
                }

                fun yFor(v: Double): Float {
                    val clamped = v.coerceIn(0.0, paddedMax)
                    val norm = (clamped / paddedMax).toFloat()
                    return (1f - norm) * size.height
                }

                val yGrid = FloatArray(gridLines + 1) { idx ->
                    (idx.toFloat() / gridLines.toFloat()) * size.height
                }

                val linePath = Path().apply {
                    safeSamples.forEachIndexed { idx, s ->
                        val x = xFor(s.elapsedMs)
                        val y = yFor(s.mBps)
                        if (idx == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }

                val fillPath = if (showFill) {
                    Path().apply {
                        addPath(linePath)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                } else {
                    null
                }

                val fillBrush = if (showFill) {
                    Brush.verticalGradient(
                        0f to fillColorTop,
                        1f to fillColorBottom,
                    )
                } else {
                    null
                }

                val last = safeSamples.last()
                val lastCenter = Offset(xFor(last.elapsedMs), yFor(last.mBps))

                onDrawBehind {
                    for (y in yGrid) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = gridStrokeWidth,
                        )
                    }

                    if (fillPath != null && fillBrush != null) {
                        drawPath(path = fillPath, brush = fillBrush)
                    }

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )

                    drawCircle(
                        color = lineColor.copy(alpha = 0.18f),
                        radius = glowRadius,
                        center = lastCenter,
                    )
                    drawCircle(
                        color = lineColor,
                        radius = dotRadius,
                        center = lastCenter,
                    )
                }
            }
        }
    )
}
