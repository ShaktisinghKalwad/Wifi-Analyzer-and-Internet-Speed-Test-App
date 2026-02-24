package com.srk.wifianalyzer.presentation.channels

import com.srk.wifianalyzer.domain.analysis.ChannelAnalysis
import com.srk.wifianalyzer.domain.analysis.ChannelAnalysisOptions
import com.srk.wifianalyzer.domain.model.WifiBand

data class ChannelUiState(
    val selectedBand: WifiBand = WifiBand.Band2G,
    val options: ChannelAnalysisOptions = ChannelAnalysisOptions(),
    val channelOverlapEnabled: Boolean = true,
    val allowedChannelWidthsMhz: Set<Int> = setOf(20, 40, 80, 160),
    val analysis: ChannelAnalysis? = null,
    val recommended20: List<Int> = emptyList(),
    val recommended80: List<Int> = emptyList(),
    val lastError: String? = null,
)
