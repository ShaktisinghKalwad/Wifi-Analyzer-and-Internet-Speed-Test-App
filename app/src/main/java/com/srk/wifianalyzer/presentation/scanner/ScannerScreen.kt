package com.srk.wifianalyzer.presentation.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import com.srk.wifianalyzer.presentation.components.AccessPointCard
import com.srk.wifianalyzer.presentation.components.AccessPointCardSkeleton
import com.srk.wifianalyzer.presentation.components.AppScaffold
import com.srk.wifianalyzer.presentation.components.MessageCard
import com.srk.wifianalyzer.presentation.components.NoNetworksFoundCard

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel(),
    onOpenApDetails: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val context = LocalContext.current

    var permissionRequestedOnce by remember { mutableStateOf(false) }
    val requiredPermissions = remember { requiredScanPermissions() }
    val compatLocationPermission = remember { Manifest.permission.ACCESS_FINE_LOCATION }
    val hasPermissions = remember {
        mutableStateOf(hasAllPermissions(context, requiredPermissions))
    }
    val hasCompatLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, compatLocationPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            hasPermissions.value = hasAllPermissions(context, requiredPermissions)
        }
    )

    val compatPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCompatLocationPermission.value = granted
        }
    )

    val locationEnabled = remember { mutableStateOf(isLocationEnabled(context)) }

    LaunchedEffect(Unit) {
        hasPermissions.value = hasAllPermissions(context, requiredPermissions)
        locationEnabled.value = isLocationEnabled(context)
        if (!hasPermissions.value && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(requiredPermissions)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermissions.value = hasAllPermissions(context, requiredPermissions)
                hasCompatLocationPermission.value =
                    ContextCompat.checkSelfPermission(context, compatLocationPermission) == PackageManager.PERMISSION_GRANTED
                locationEnabled.value = isLocationEnabled(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppScaffold(
        title = "Wi‑Fi Analyzer",
        titleIcon = Icons.Filled.Wifi,
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            if (!hasPermissions.value) {
                PermissionRequiredCard(
                    onRequest = { permissionLauncher.launch(requiredPermissions) }
                )
                return@Column
            }

            if (!locationEnabled.value && Build.VERSION.SDK_INT < 33) {
                LocationRequiredCard(
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                )
                return@Column
            }

            val contentState = when {
                uiState.lastError != null -> 3
                uiState.accessPoints.isNotEmpty() -> 2
                uiState.isScanning -> 0
                else -> 1
            }

            Crossfade(
                targetState = contentState,
                label = "scannerContent",
            ) { state ->
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.onPullToRefresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (state) {
                        0 -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(6) {
                                    AccessPointCardSkeleton()
                                }
                            }
                        }
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NoNetworksFoundCard()
                            }
                        }
                        3 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ScanErrorCard(
                                    message = uiState.lastError.orEmpty(),
                                    onOpenWifiSettings = {
                                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                    },
                                )

                                if (Build.VERSION.SDK_INT >= 33 && !locationEnabled.value) {
                                    LocationRequiredCard(
                                        onOpenSettings = {
                                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        }
                                    )
                                }

                                if (Build.VERSION.SDK_INT >= 33 && !hasCompatLocationPermission.value) {
                                    MessageCard(
                                        modifier = Modifier.padding(16.dp),
                                        title = "Try compatibility mode",
                                        message = "On some Android 14 devices, Wi‑Fi scan results may require Location permission in addition to Nearby Wi‑Fi devices permission.",
                                        actionText = "Grant Location permission",
                                        onAction = { compatPermissionLauncher.launch(compatLocationPermission) },
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = uiState.accessPoints,
                                    key = { "${it.bssid}_${it.ssid}_${it.frequencyMhz}" }
                                ) { ap ->
                                    AccessPointCard(
                                        ap = ap,
                                        signalUnit = uiState.signalUnit,
                                        onClick = { onOpenApDetails(ap.bssid) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    onRequest: () -> Unit,
) {
    MessageCard(
        modifier = Modifier.padding(16.dp),
        title = "Permissions required",
        message = "Wi‑Fi scan results require location or nearby devices permission. The app does not collect your location; Android enforces this permission for Wi‑Fi scanning.",
        actionText = "Grant permissions",
        onAction = onRequest,
    )
}

@Composable
private fun LocationRequiredCard(
    onOpenSettings: () -> Unit,
) {
    MessageCard(
        modifier = Modifier.padding(16.dp),
        title = "Location services required",
        message = "Wi‑Fi scanning may require Location services to be enabled to receive scan results.",
        actionText = "Open Location settings",
        onAction = onOpenSettings,
    )
}

@Composable
private fun ScanErrorCard(
    message: String,
    onOpenWifiSettings: () -> Unit,
) {
    MessageCard(
        modifier = Modifier.padding(16.dp),
        title = "Scan results unavailable",
        message = message,
        actionText = "Open Wi‑Fi settings",
        onAction = onOpenWifiSettings,
    )
}

private fun requiredScanPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun hasAllPermissions(context: android.content.Context, permissions: Array<String>): Boolean {
    return permissions.all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isLocationEnabled(context: android.content.Context): Boolean {
    val lm = context.getSystemService(LocationManager::class.java) ?: return false
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
