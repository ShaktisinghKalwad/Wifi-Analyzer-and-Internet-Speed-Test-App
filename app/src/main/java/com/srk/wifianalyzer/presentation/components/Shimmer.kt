package com.srk.wifianalyzer.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.srk.wifianalyzer.presentation.components.Spacing

@Composable
fun AccessPointCardSkeleton(
    modifier: Modifier = Modifier,
) {
    val brush = rememberShimmerBrush()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 84.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBlock(
                modifier = Modifier
                    .height(40.dp)
                    .width(24.dp),
                brush = brush,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.s8),
                verticalArrangement = Arrangement.spacedBy(Spacing.s8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(18.dp),
                        brush = brush,
                    )
                    ShimmerBlock(
                        modifier = Modifier
                            .width(52.dp)
                            .height(16.dp),
                        brush = brush,
                    )
                }
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp),
                    brush = brush,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    repeat(4) {
                        ShimmerBlock(
                            modifier = Modifier
                                .width(56.dp)
                                .height(24.dp),
                            brush = brush,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.60f)
                        .height(12.dp),
                    brush = brush,
                )
            }
        }
    }
}

@Composable
fun ShimmerBlock(
    modifier: Modifier,
    brush: Brush,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(brush)
    ) {}
}

@Composable
fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )

    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - 500f, 0f),
        end = Offset(x, 0f),
    )
}
