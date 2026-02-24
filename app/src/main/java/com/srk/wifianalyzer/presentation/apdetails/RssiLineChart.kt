package com.srk.wifianalyzer.presentation.apdetails

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.srk.wifianalyzer.ui.LocalGraphDensity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun RssiLineChart(
    modifier: Modifier,
    samples: List<RssiSample>,
    smooth: Boolean = false,
    smoothAlpha: Float = 0.25f,
) {
    val graphDensity = LocalGraphDensity.current
    val gridLines = (4f * graphDensity).roundToInt().coerceIn(2, 8)
    val gridStrokeWidth = 2f * graphDensity
    val lineStrokeWidth = 6f * graphDensity
    val dotRadius = 8f * graphDensity

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val drawSamples = if (smooth) smoothSamples(samples, smoothAlpha) else samples
        if (drawSamples.size < 2) return@Canvas

        val minDbm = -100
        val maxDbm = -20

        val minT = drawSamples.minOf { it.timestampMs }
        val maxT = drawSamples.maxOf { it.timestampMs }
        val tRange = max(1L, maxT - minT)

        fun xFor(t: Long): Float {
            val norm = (t - minT).toFloat() / tRange.toFloat()
            return norm * size.width
        }

        fun yFor(dbm: Int): Float {
            val clamped = min(maxDbm, max(minDbm, dbm))
            val norm = (clamped - minDbm).toFloat() / (maxDbm - minDbm).toFloat()
            return (1f - norm) * size.height
        }

        for (i in 0..gridLines) {
            val y = (i.toFloat() / gridLines.toFloat()) * size.height
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = gridStrokeWidth
            )
        }

        val path = Path()
        drawSamples.forEachIndexed { idx, s ->
            val x = xFor(s.timestampMs)
            val y = yFor(s.rssiDbm)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round)
        )

        val last = drawSamples.last()
        drawCircle(
            color = dotColor,
            radius = dotRadius,
            center = Offset(xFor(last.timestampMs), yFor(last.rssiDbm))
        )
    }
}

private fun smoothSamples(samples: List<RssiSample>, alpha: Float): List<RssiSample> {
    if (samples.size < 2) return samples
    val a = alpha.coerceIn(0.05f, 0.8f)
    var prev = samples.first().rssiDbm.toFloat()
    val out = ArrayList<RssiSample>(samples.size)
    samples.forEachIndexed { idx, s ->
        val smoothed = if (idx == 0) prev else (a * s.rssiDbm.toFloat() + (1f - a) * prev)
        prev = smoothed
        out.add(s.copy(rssiDbm = smoothed.roundToInt()))
    }
    return out
}
