package com.srk.wifianalyzer.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.srk.wifianalyzer.presentation.components.Spacing

@Composable
fun NoNetworksFoundCard(
    modifier: Modifier = Modifier,
    title: String = "No networks found",
    message: String = "No Wi‑Fi access points were found nearby. Move closer to an AP or wait for the next scan.",
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s16)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.s8)
            )
        }
    }
}

@Composable
fun MessageCard(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s16)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.s8)
            )
            if (actionText != null && onAction != null) {
                Row(modifier = Modifier.padding(top = Spacing.s12)) {
                    Button(onClick = onAction) {
                        Text(text = actionText)
                    }
                }
            }
        }
    }
}
