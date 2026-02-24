package com.srk.wifianalyzer.settings.presentation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srk.wifianalyzer.R
import com.srk.wifianalyzer.presentation.components.AppScaffold
import com.srk.wifianalyzer.presentation.components.Spacing
import com.srk.wifianalyzer.settings.domain.models.ExportFormat
import com.srk.wifianalyzer.settings.domain.models.HistoryRetention
import com.srk.wifianalyzer.settings.domain.models.ScanInterval
import com.srk.wifianalyzer.settings.domain.models.SettingsTheme
import com.srk.wifianalyzer.settings.domain.models.SignalUnit
import com.srk.wifianalyzer.settings.domain.models.SpeedTestGraphStyle
import com.srk.wifianalyzer.settings.domain.models.SpeedTestMode
import com.srk.wifianalyzer.settings.domain.models.SpeedTestServerSelection
import com.srk.wifianalyzer.settings.domain.models.SpeedUnit
import com.srk.wifianalyzer.settings.domain.models.ThreadPriority
import com.srk.wifianalyzer.settings.presentation.components.SettingsDropdown
import com.srk.wifianalyzer.settings.presentation.components.SettingsSection
import com.srk.wifianalyzer.settings.presentation.components.SettingsSlider
import com.srk.wifianalyzer.settings.presentation.components.SettingsSwitch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsViewModel.Effect.ShowMessageRes -> {
                    val message = if (effect.formatArgs.isEmpty()) {
                        context.getString(effect.resId)
                    } else {
                        context.getString(effect.resId, *effect.formatArgs.toTypedArray())
                    }
                    snackbarHostState.showSnackbar(message)
                }
                SettingsViewModel.Effect.OpenAppSettings -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = null) },
            title = { Text(text = stringResource(R.string.settings_reset_title)) },
            text = { Text(text = stringResource(R.string.settings_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetToDefaults()
                    },
                ) {
                    Text(text = stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    AppScaffold(
        title = stringResource(R.string.settings_title),
        navigationIcon = {
            IconButton(onClick = {}, enabled = false) {
                Icon(imageVector = Icons.Filled.Info, contentDescription = null)
            }
        },
        actions = {
            TextButton(onClick = { showResetDialog = true }) {
                Text(text = stringResource(R.string.settings_reset_action))
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentModifier ->
        val scrollState = rememberScrollState()
        Box(
            modifier = contentModifier
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 680.dp)
                    .padding(Spacing.s16)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(Spacing.s12),
            ) {
                SettingsSection(
                    title = stringResource(R.string.settings_section_ui_title),
                    description = stringResource(R.string.settings_section_ui_desc),
                ) {
                    SettingsDropdown(
                        title = stringResource(R.string.settings_theme),
                        selected = uiState.settings.ui.theme,
                        options = SettingsTheme.entries.toList(),
                        optionLabel = {
                            when (it) {
                                SettingsTheme.System -> "System"
                                SettingsTheme.Light -> "Light"
                                SettingsTheme.Dark -> "Dark"
                            }
                        },
                        onSelected = viewModel::setTheme,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_dynamic_color),
                        checked = uiState.settings.ui.dynamicColorEnabled,
                        onCheckedChange = viewModel::setDynamicColorEnabled,
                        description = stringResource(R.string.settings_dynamic_color_desc),
                    )

                    SettingsSlider(
                        title = stringResource(R.string.settings_font_scale),
                        value = uiState.settings.ui.fontScale,
                        onValueChange = viewModel::setFontScale,
                        valueText = String.format("%.2f×", uiState.settings.ui.fontScale),
                        valueRange = 0.85f..1.3f,
                    )

                    SettingsSlider(
                        title = stringResource(R.string.settings_graph_density),
                        value = uiState.settings.ui.graphDensity,
                        onValueChange = viewModel::setGraphDensity,
                        valueText = String.format("%.2f×", uiState.settings.ui.graphDensity),
                        valueRange = 0.5f..2.0f,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_pro_mode),
                        checked = uiState.settings.ui.proModeEnabled,
                        onCheckedChange = viewModel::setProModeEnabled,
                        description = stringResource(R.string.settings_pro_mode_desc),
                    )
                }

                SettingsSection(
                    title = stringResource(R.string.settings_section_wifi_title),
                    description = stringResource(R.string.settings_section_wifi_desc),
                ) {
                    SettingsDropdown(
                        title = stringResource(R.string.settings_scan_interval),
                        selected = uiState.settings.wifi.scanInterval,
                        options = ScanInterval.entries.toList(),
                        optionLabel = { "${it.seconds}s" },
                        onSelected = viewModel::setScanInterval,
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.settings_signal_unit),
                        selected = uiState.settings.wifi.signalUnit,
                        options = SignalUnit.entries.toList(),
                        optionLabel = {
                            when (it) {
                                SignalUnit.Dbm -> "dBm"
                                SignalUnit.Percentage -> "%"
                            }
                        },
                        onSelected = viewModel::setSignalUnit,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_band_2g),
                        checked = uiState.settings.wifi.band2gEnabled,
                        onCheckedChange = viewModel::setBand2gEnabled,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_band_5g),
                        checked = uiState.settings.wifi.band5gEnabled,
                        onCheckedChange = viewModel::setBand5gEnabled,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_band_6g),
                        checked = uiState.settings.wifi.band6gEnabled,
                        onCheckedChange = viewModel::setBand6gEnabled,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_channel_overlap),
                        checked = uiState.settings.wifi.channelOverlapEnabled,
                        onCheckedChange = viewModel::setChannelOverlapEnabled,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_rssi_smoothing),
                        checked = uiState.settings.wifi.rssiSmoothingEnabled,
                        onCheckedChange = viewModel::setRssiSmoothingEnabled,
                    )

                    SettingsSlider(
                        title = stringResource(R.string.settings_rssi_smoothing_strength),
                        value = uiState.settings.wifi.rssiSmoothingAlpha,
                        onValueChange = viewModel::setRssiSmoothingAlpha,
                        valueText = "${(uiState.settings.wifi.rssiSmoothingAlpha * 100).roundToInt()}%",
                        valueRange = 0f..1f,
                        enabled = uiState.settings.wifi.rssiSmoothingEnabled,
                    )

                    if (uiState.settings.ui.proModeEnabled) {
                        SettingsSwitch(
                            title = stringResource(R.string.settings_width_20),
                            checked = uiState.settings.wifi.showChannelWidths.contains(20),
                            onCheckedChange = { viewModel.setChannelWidthShown(20, it) },
                        )
                        SettingsSwitch(
                            title = stringResource(R.string.settings_width_40),
                            checked = uiState.settings.wifi.showChannelWidths.contains(40),
                            onCheckedChange = { viewModel.setChannelWidthShown(40, it) },
                        )
                        SettingsSwitch(
                            title = stringResource(R.string.settings_width_80),
                            checked = uiState.settings.wifi.showChannelWidths.contains(80),
                            onCheckedChange = { viewModel.setChannelWidthShown(80, it) },
                        )
                        SettingsSwitch(
                            title = stringResource(R.string.settings_width_160),
                            checked = uiState.settings.wifi.showChannelWidths.contains(160),
                            onCheckedChange = { viewModel.setChannelWidthShown(160, it) },
                        )
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.settings_section_speed_test_title),
                    description = stringResource(R.string.settings_section_speed_test_desc),
                ) {
                    var manualUrlText by remember(uiState.settings.speedTest.manualServerUrl) {
                        mutableStateOf(uiState.settings.speedTest.manualServerUrl)
                    }

                    LaunchedEffect(
                        manualUrlText,
                        uiState.settings.speedTest.manualServerUrl,
                        uiState.settings.speedTest.serverSelection,
                    ) {
                        if (uiState.settings.speedTest.serverSelection != SpeedTestServerSelection.Manual) return@LaunchedEffect
                        if (manualUrlText == uiState.settings.speedTest.manualServerUrl) return@LaunchedEffect
                        delay(450)
                        viewModel.setManualServerUrl(manualUrlText)
                    }

                    SettingsDropdown(
                        title = stringResource(R.string.settings_speed_unit),
                        selected = uiState.settings.speedTest.speedUnit,
                        options = SpeedUnit.entries.toList(),
                        optionLabel = {
                            when (it) {
                                SpeedUnit.MBps -> stringResource(R.string.unit_mb_per_s)
                                SpeedUnit.Mbps -> stringResource(R.string.unit_mbps)
                            }
                        },
                        onSelected = viewModel::setSpeedUnit,
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.settings_speed_test_mode),
                        selected = uiState.settings.speedTest.mode,
                        options = SpeedTestMode.entries.toList(),
                        optionLabel = {
                            when (it) {
                                SpeedTestMode.DownloadOnly -> "Download only"
                                SpeedTestMode.UploadOnly -> "Upload only"
                                SpeedTestMode.Full -> "Full"
                            }
                        },
                        onSelected = viewModel::setSpeedTestMode,
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.settings_server_selection),
                        selected = uiState.settings.speedTest.serverSelection,
                        options = SpeedTestServerSelection.entries.toList(),
                        optionLabel = {
                            when (it) {
                                SpeedTestServerSelection.AutoNearest -> "Auto (nearest)"
                                SpeedTestServerSelection.Manual -> "Manual"
                            }
                        },
                        onSelected = viewModel::setServerSelection,
                    )

                    if (uiState.settings.speedTest.serverSelection == SpeedTestServerSelection.Manual) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.s16),
                            value = manualUrlText,
                            onValueChange = { manualUrlText = it },
                            label = { Text(text = stringResource(R.string.settings_manual_server_url)) },
                            singleLine = true,
                        )
                    }

                    SettingsSwitch(
                        title = stringResource(R.string.settings_jitter_packet_loss),
                        checked = uiState.settings.speedTest.jitterAndPacketLossEnabled,
                        onCheckedChange = viewModel::setJitterAndPacketLossEnabled,
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.settings_graph_style),
                        selected = uiState.settings.speedTest.graphStyle,
                        options = SpeedTestGraphStyle.entries.toList(),
                        optionLabel = {
                            when (it) {
                                SpeedTestGraphStyle.Line -> "Line"
                                SpeedTestGraphStyle.Filled -> "Filled"
                            }
                        },
                        onSelected = viewModel::setSpeedTestGraphStyle,
                    )
                }

                SettingsSection(
                    title = stringResource(R.string.settings_section_alerts_title),
                    description = stringResource(R.string.settings_section_alerts_desc),
                ) {
                    SettingsSlider(
                        title = stringResource(R.string.settings_weak_signal_threshold),
                        value = uiState.settings.alerts.weakSignalThresholdDbm.toFloat(),
                        onValueChange = { viewModel.setWeakSignalThresholdDbm(it.roundToInt()) },
                        valueText = "${uiState.settings.alerts.weakSignalThresholdDbm} dBm",
                        valueRange = -100f..-30f,
                        steps = 69,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_wifi_disconnect_notification),
                        checked = uiState.settings.alerts.wifiDisconnectNotificationEnabled,
                        onCheckedChange = viewModel::setWifiDisconnectNotificationEnabled,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_network_change_detection),
                        checked = uiState.settings.alerts.networkChangeDetectionEnabled,
                        onCheckedChange = viewModel::setNetworkChangeDetectionEnabled,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_background_auto_scan),
                        checked = uiState.settings.alerts.backgroundAutoScanEnabled,
                        onCheckedChange = viewModel::setBackgroundAutoScanEnabled,
                        description = stringResource(R.string.settings_background_auto_scan_desc),
                    )
                }

                SettingsSection(
                    title = stringResource(R.string.settings_section_history_title),
                    description = stringResource(R.string.settings_section_history_desc),
                ) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_enable_history),
                        checked = uiState.settings.history.scanHistoryEnabled,
                        onCheckedChange = viewModel::setScanHistoryEnabled,
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.settings_history_retention),
                        selected = uiState.settings.history.retention,
                        options = HistoryRetention.entries.toList(),
                        optionLabel = {
                            when (it) {
                                HistoryRetention.OneHour -> stringResource(R.string.settings_retention_1h)
                                HistoryRetention.TwentyFourHours -> stringResource(R.string.settings_retention_24h)
                                HistoryRetention.SevenDays -> stringResource(R.string.settings_retention_7d)
                            }
                        },
                        onSelected = viewModel::setHistoryRetention,
                        enabled = uiState.settings.history.scanHistoryEnabled,
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.settings_export_format),
                        selected = uiState.settings.history.exportFormat,
                        options = ExportFormat.entries.toList(),
                        optionLabel = {
                            when (it) {
                                ExportFormat.Csv -> "CSV"
                                ExportFormat.Json -> "JSON"
                            }
                        },
                        onSelected = viewModel::setExportFormat,
                        enabled = uiState.settings.history.scanHistoryEnabled,
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_auto_clear_logs),
                        checked = uiState.settings.history.autoClearOldLogs,
                        onCheckedChange = viewModel::setAutoClearOldLogs,
                        enabled = uiState.settings.history.scanHistoryEnabled,
                    )
                }

                SettingsSection(
                    title = stringResource(R.string.settings_section_performance_title),
                    description = stringResource(R.string.settings_section_performance_desc),
                    initiallyExpanded = uiState.settings.ui.proModeEnabled,
                ) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_battery_saver),
                        checked = uiState.settings.performance.batterySaverModeEnabled,
                        onCheckedChange = viewModel::setBatterySaverEnabled,
                        description = stringResource(R.string.settings_battery_saver_desc),
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_adaptive_scan_engine),
                        checked = uiState.settings.performance.adaptiveScanEngineEnabled,
                        onCheckedChange = viewModel::setAdaptiveScanEngineEnabled,
                    )

                    if (uiState.settings.ui.proModeEnabled) {
                        SettingsDropdown(
                            title = stringResource(R.string.settings_thread_priority),
                            selected = uiState.settings.performance.threadPriority,
                            options = ThreadPriority.entries.toList(),
                            optionLabel = {
                                when (it) {
                                    ThreadPriority.Normal -> "Normal"
                                    ThreadPriority.Background -> "Background"
                                    ThreadPriority.High -> "High"
                                }
                            },
                            onSelected = viewModel::setThreadPriority,
                        )
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.settings_section_privacy_title),
                    description = stringResource(R.string.settings_section_privacy_desc),
                    initiallyExpanded = false,
                ) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_local_only_processing),
                        checked = uiState.settings.privacy.localOnlyProcessingEnabled,
                        onCheckedChange = viewModel::setLocalOnlyProcessingEnabled,
                    )

                    Button(
                        onClick = viewModel::openAppSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.s16),
                    ) {
                        Text(text = stringResource(R.string.settings_open_permission_settings))
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.settings_section_developer_title),
                    description = stringResource(R.string.settings_section_developer_desc),
                    initiallyExpanded = false,
                ) {
                    Text(
                        text = stringResource(R.string.settings_app_version_tap_hint),
                        modifier = Modifier
                            .padding(horizontal = Spacing.s16, vertical = Spacing.s8)
                            .semantics { },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    TextButton(
                        onClick = viewModel::onAppVersionTapped,
                        modifier = Modifier.padding(horizontal = Spacing.s16),
                    ) {
                        Text(text = stringResource(R.string.settings_app_version_row))
                    }

                    if (uiState.settings.developer.developerModeEnabled) {
                        SettingsSwitch(
                            title = stringResource(R.string.settings_dev_raw_scan_data),
                            checked = uiState.settings.developer.showRawScanData,
                            onCheckedChange = viewModel::setShowRawScanData,
                        )
                        SettingsSwitch(
                            title = stringResource(R.string.settings_dev_phy_mode),
                            checked = uiState.settings.developer.showPhyMode,
                            onCheckedChange = viewModel::setShowPhyMode,
                        )
                        SettingsSwitch(
                            title = stringResource(R.string.settings_dev_link_speed),
                            checked = uiState.settings.developer.showLinkSpeed,
                            onCheckedChange = viewModel::setShowLinkSpeed,
                        )
                    }
                }
            }
        }
    }
}
