package com.srk.wifianalyzer.presentation.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srk.wifianalyzer.R
import com.srk.wifianalyzer.domain.model.WifiBand
import com.srk.wifianalyzer.domain.model.WifiChannelWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import com.srk.wifianalyzer.presentation.components.AppScaffold

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChannelScreen(
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var graphMode by remember { mutableStateOf<GraphMode>(GraphMode.Score) }

    LaunchedEffect(uiState.channelOverlapEnabled) {
        if (!uiState.channelOverlapEnabled && graphMode == GraphMode.Overlap) {
            graphMode = GraphMode.Score
        }
    }

    LaunchedEffect(uiState.selectedBand) {
        if (uiState.selectedBand != WifiBand.Band2G && uiState.options.preferNonOverlapping2g) {
            viewModel.setPreferNonOverlapping2g(false)
        }

        if (uiState.selectedBand != WifiBand.Band6G && uiState.options.preferPsc6g) {
            viewModel.setPreferPsc6g(false)
        }
    }

    AppScaffold(
        title = stringResource(R.string.channel_analyzer_title),
        actions = {
            IconButton(onClick = { showSettings = true }) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = stringResource(R.string.channel_analyzer_settings))
            }
        },
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            uiState.lastError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            BandSelector(
                selected = uiState.selectedBand,
                onSelect = viewModel::selectBand,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val analysis = uiState.analysis
            if (analysis == null) {
                Text(
                    text = stringResource(R.string.channel_analyzer_waiting_for_scan),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                if (analysis.bestChannels.isNotEmpty()) {
                    ElevatedCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.channel_analyzer_recommended_width,
                                uiState.options.recommendationWidth.mhz,
                                analysis.bestChannels.joinToString(),
                            ),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                if (uiState.recommended20.isNotEmpty() || uiState.recommended80.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (20 in uiState.allowedChannelWidthsMhz) {
                            ElevatedCard(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.channel_analyzer_recommended_width,
                                        20,
                                        uiState.recommended20.joinToString(),
                                    ),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        if (uiState.selectedBand != WifiBand.Band2G && 80 in uiState.allowedChannelWidthsMhz) {
                            ElevatedCard(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.channel_analyzer_recommended_width,
                                        80,
                                        uiState.recommended80.joinToString(),
                                    ),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (graphMode == GraphMode.Score) {
                        stringResource(R.string.channel_analyzer_graph_title)
                    } else {
                        stringResource(R.string.channel_analyzer_overlap_title)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = graphMode == GraphMode.Score,
                        onClick = { graphMode = GraphMode.Score },
                        label = { Text(text = stringResource(R.string.channel_analyzer_graph_mode_score)) },
                    )
                    if (uiState.channelOverlapEnabled) {
                        FilterChip(
                            selected = graphMode == GraphMode.Overlap,
                            onClick = { graphMode = GraphMode.Overlap },
                            label = { Text(text = stringResource(R.string.channel_analyzer_graph_mode_overlap)) },
                        )
                    }
                }

                if (graphMode == GraphMode.Score) {
                    ChannelScoreGraph(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        analysis = analysis,
                    )
                } else if (uiState.channelOverlapEnabled) {
                    ChannelOverlapGraph(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        analysis = analysis,
                        recommendationWidthMhz = uiState.options.recommendationWidth.mhz,
                    )
                }

                if (analysis.bestChannels.isNotEmpty()) {
                    ElevatedCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.channel_analyzer_why_recommended),
                                style = MaterialTheme.typography.titleMedium,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            analysis.bestChannels.forEach { channel ->
                                val score = analysis.channels.firstOrNull { it.channel == channel }
                                Text(
                                    text = stringResource(R.string.channel_analyzer_channel_label, channel),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val interferers = score?.topInterferers.orEmpty()
                                if (interferers.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.channel_analyzer_no_interferers),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    interferers.forEach { i ->
                                        Text(
                                            text = stringResource(
                                                R.string.channel_analyzer_interferer_line,
                                                i.ssid,
                                                i.rssiDbm,
                                                i.channel,
                                                i.widthMhz,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.channel_analyzer_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.channel_analyzer_avoid_dfs))
                            Text(
                                text = stringResource(R.string.channel_analyzer_avoid_dfs_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.options.avoidDfs,
                            onCheckedChange = viewModel::setAvoidDfs,
                            enabled = uiState.selectedBand == WifiBand.Band5G,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.channel_analyzer_prefer_2g_non_overlapping))
                            Text(
                                text = stringResource(R.string.channel_analyzer_prefer_2g_non_overlapping_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.options.preferNonOverlapping2g,
                            onCheckedChange = viewModel::setPreferNonOverlapping2g,
                            enabled = uiState.selectedBand == WifiBand.Band2G,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.channel_analyzer_prefer_psc_6g))
                            Text(
                                text = stringResource(R.string.channel_analyzer_prefer_psc_6g_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.options.preferPsc6g,
                            onCheckedChange = viewModel::setPreferPsc6g,
                            enabled = uiState.selectedBand == WifiBand.Band6G,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.channel_analyzer_recommendation_width))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (20 in uiState.allowedChannelWidthsMhz) {
                                WidthChip(
                                    width = WifiChannelWidth.W20,
                                    selected = uiState.options.recommendationWidth == WifiChannelWidth.W20,
                                    onSelect = viewModel::setRecommendationWidth,
                                )
                            }
                            if (40 in uiState.allowedChannelWidthsMhz) {
                                WidthChip(
                                    width = WifiChannelWidth.W40,
                                    selected = uiState.options.recommendationWidth == WifiChannelWidth.W40,
                                    onSelect = viewModel::setRecommendationWidth,
                                )
                            }
                            if (80 in uiState.allowedChannelWidthsMhz) {
                                WidthChip(
                                    width = WifiChannelWidth.W80,
                                    selected = uiState.options.recommendationWidth == WifiChannelWidth.W80,
                                    onSelect = viewModel::setRecommendationWidth,
                                )
                            }
                            if (160 in uiState.allowedChannelWidthsMhz) {
                                WidthChip(
                                    width = WifiChannelWidth.W160,
                                    selected = uiState.options.recommendationWidth == WifiChannelWidth.W160,
                                    onSelect = viewModel::setRecommendationWidth,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WidthChip(
    width: WifiChannelWidth,
    selected: Boolean,
    onSelect: (WifiChannelWidth) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(width) },
        label = { Text(text = "${width.mhz} MHz") },
    )
}

private enum class GraphMode {
    Score,
    Overlap,
}

@Composable
private fun BandSelector(
    selected: WifiBand,
    onSelect: (WifiBand) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == WifiBand.Band2G,
            onClick = { onSelect(WifiBand.Band2G) },
            label = { Text(text = "2.4 GHz") },
        )
        FilterChip(
            selected = selected == WifiBand.Band5G,
            onClick = { onSelect(WifiBand.Band5G) },
            label = { Text(text = "5 GHz") },
        )
        FilterChip(
            selected = selected == WifiBand.Band6G,
            onClick = { onSelect(WifiBand.Band6G) },
            label = { Text(text = "6 GHz") },
        )
    }
}
