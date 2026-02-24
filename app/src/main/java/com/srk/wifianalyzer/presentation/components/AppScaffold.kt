package com.srk.wifianalyzer.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.RowScope

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    titleIcon: ImageVector? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = contentWindowInsets,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (titleIcon != null) {
                            Icon(imageVector = titleIcon, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = title)
                    }
                },
                navigationIcon = navigationIcon ?: {},
                actions = actions,
            )
        },
    ) { innerPadding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
