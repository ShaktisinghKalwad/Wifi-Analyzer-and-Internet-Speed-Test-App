package com.srk.wifianalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.srk.wifianalyzer.presentation.AppRoot
import com.srk.wifianalyzer.settings.domain.models.SettingsTheme
import com.srk.wifianalyzer.settings.presentation.AppSettingsViewModel
import com.srk.wifianalyzer.ui.LocalGraphDensity
import com.srk.wifianalyzer.ui.theme.WifiAnalyzerTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRootWithSettings()
        }
    }
}

@Composable
private fun AppRootWithSettings(
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val settings by appSettingsViewModel.userSettings.collectAsState()
    val systemDark = isSystemInDarkTheme()

    val darkTheme = when (settings.ui.theme) {
        SettingsTheme.System -> systemDark
        SettingsTheme.Light -> false
        SettingsTheme.Dark -> true
    }

    val baseDensity = LocalDensity.current
    val density = Density(baseDensity.density, fontScale = settings.ui.fontScale)

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalGraphDensity provides settings.ui.graphDensity,
    ) {
        WifiAnalyzerTheme(
            darkTheme = darkTheme,
            dynamicColor = settings.ui.dynamicColorEnabled,
        ) {
            AppRoot()
        }
    }
}