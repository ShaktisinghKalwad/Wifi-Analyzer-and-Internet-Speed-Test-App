package com.srk.wifianalyzer.domain.analysis

import com.srk.wifianalyzer.domain.model.WifiBand
import com.srk.wifianalyzer.domain.model.WifiChannelWidth

data class ChannelAnalysisOptions(
    val avoidDfs: Boolean = true,
    val preferNonOverlapping2g: Boolean = true,
    val preferPsc6g: Boolean = false,
    val recommendationWidth: WifiChannelWidth = WifiChannelWidth.W20,
)

data class ChannelObservedAp(
    val ssid: String,
    val rssiDbm: Int,
    val channel: Int,
    val widthMhz: Int,
)

data class ChannelInterferer(
    val ssid: String,
    val rssiDbm: Int,
    val channel: Int,
    val widthMhz: Int,
    val contribution: Double,
)

data class ChannelScore(
    val channel: Int,
    val score: Double,
    val topInterferers: List<ChannelInterferer> = emptyList(),
)

data class ChannelAnalysis(
    val band: WifiBand,
    val channels: List<ChannelScore>,
    val bestChannels: List<Int>,
    val observedAps: List<ChannelObservedAp> = emptyList(),
)
