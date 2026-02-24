package com.srk.wifianalyzer.presentation.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.srk.wifianalyzer.presentation.components.AppScaffold
import com.srk.wifianalyzer.presentation.components.MessageCard

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ToolsScreen() {
    AppScaffold(
        title = "Tools",
        navigationIcon = {
            IconButton(onClick = {}, enabled = false) {
                Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
            }
        },
    ) { contentModifier ->
        Column(
            modifier = contentModifier.padding(16.dp)
        ) {
            MessageCard(
                title = "Tools (coming soon)",
                message = "This tab will host extra utilities and diagnostics.",
            )
        }
    }
}
