package com.srk.wifianalyzer.domain.analysis

import com.srk.wifianalyzer.domain.model.WifiAccessPoint
import com.srk.wifianalyzer.domain.model.WifiBand
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.pow

class ComputeChannelAnalysisUseCase @Inject constructor() {

    operator fun invoke(
        band: WifiBand,
        accessPoints: List<WifiAccessPoint>,
        options: ChannelAnalysisOptions = ChannelAnalysisOptions(),
        maxRecommendations: Int = 3,
    ): ChannelAnalysis {
        val channels = candidateChannels(band = band, options = options)

        if (channels.isEmpty()) {
            return ChannelAnalysis(band = band, channels = emptyList(), bestChannels = emptyList(), observedAps = emptyList())
        }

        val aps = accessPoints.filter { it.band == band && it.channel != null }
        val observedAps = aps.map {
            ChannelObservedAp(
                ssid = it.ssid,
                rssiDbm = it.rssiDbm,
                channel = it.channel!!,
                widthMhz = it.channelWidth.mhz,
            )
        }

        val scores = channels.map { ch ->
            val contributions = aps.map { ap ->
                val center = ap.channel!!.toDouble()
                val effectiveWidthMhz = maxOf(ap.channelWidth.mhz, options.recommendationWidth.mhz)
                val sigma = sigmaChannels(effectiveWidthMhz)
                val overlap = gaussianOverlap(ch.toDouble(), center, sigma)
                val power = dbmToLinear(ap.rssiDbm)
                val contribution = power * overlap
                ChannelInterferer(
                    ssid = ap.ssid,
                    rssiDbm = ap.rssiDbm,
                    channel = ap.channel!!,
                    widthMhz = ap.channelWidth.mhz,
                    contribution = contribution,
                )
            }

            val score = contributions.sumOf { it.contribution }

            ChannelScore(
                channel = ch,
                score = score,
                topInterferers = contributions
                    .sortedByDescending { it.contribution }
                    .take(3),
            )
        }

        val best = scores
            .sortedBy { it.score }
            .take(maxRecommendations)
            .map { it.channel }

        return ChannelAnalysis(
            band = band,
            channels = scores,
            bestChannels = best,
            observedAps = observedAps,
        )
    }

    private fun candidateChannels(
        band: WifiBand,
        options: ChannelAnalysisOptions,
    ): List<Int> {
        val base = when (band) {
            WifiBand.Band2G -> (1..13).toList()
            WifiBand.Band5G -> listOf(
                36, 40, 44, 48,
                52, 56, 60, 64,
                100, 104, 108, 112, 116, 120, 124, 128,
                132, 136, 140, 144,
                149, 153, 157, 161, 165,
            )
            WifiBand.Band6G -> (1..233 step 4).toList()
            WifiBand.Unknown -> emptyList()
        }

        val after2g = if (band == WifiBand.Band2G && options.preferNonOverlapping2g) {
            listOf(1, 6, 11)
        } else {
            base
        }

        val afterDfs = if (band == WifiBand.Band5G && options.avoidDfs) {
            after2g.filter { ch -> ch < 52 || ch > 144 }
        } else {
            after2g
        }

        val afterPsc = if (band == WifiBand.Band6G && options.preferPsc6g) {
            val psc = setOf(5, 21, 37, 53, 69, 85, 101, 117, 133, 149, 165, 181, 197, 213, 229)
            afterDfs.filter { it in psc }
        } else {
            afterDfs
        }

        return afterPsc
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
}
