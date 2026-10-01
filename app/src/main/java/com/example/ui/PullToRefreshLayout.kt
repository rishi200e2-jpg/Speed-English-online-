package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Universal Pull-to-Refresh Layout:
 * Supports unlimited pull-down-to-refresh gestures across all screens and tabs.
 * Highly responsive, fluid M3 spring animation with light & dark theme styling.
 */
@Composable
fun PullToRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val maxPullDistance = 90.dp
    val triggerThreshold = 42.dp
    val density = LocalDensity.current
    val maxPullPx = with(density) { maxPullDistance.toPx() }
    val triggerThresholdPx = with(density) { triggerThreshold.toPx() }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullDistance = 0f
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isRefreshing) maxPullPx * 0.55f else pullDistance,
        animationSpec = if (isRefreshing) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        },
        label = "pull_to_refresh_offset"
    )

    val nestedScrollConnection = remember(isRefreshing, maxPullPx, triggerThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isRefreshing) return Offset.Zero
                // If user is dragging upward while pulled down, consume up-scroll to close indicator
                if (available.y < 0 && pullDistance > 0f) {
                    val prev = pullDistance
                    pullDistance = (pullDistance + available.y).coerceAtLeast(0f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isRefreshing) return Offset.Zero
                // If user is dragging downward and at top of scrollable, consume pull
                if (available.y > 0) {
                    val prev = pullDistance
                    pullDistance = (pullDistance + available.y * 0.55f).coerceAtMost(maxPullPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isRefreshing && pullDistance >= triggerThresholdPx) {
                    onRefresh()
                }
                pullDistance = 0f
                return super.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!isRefreshing && pullDistance >= triggerThresholdPx) {
                    onRefresh()
                }
                pullDistance = 0f
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = modifier.nestedScroll(nestedScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = animatedOffset
                }
        ) {
            content()
        }

        if (animatedOffset > 4f || isRefreshing) {
            val progress = (animatedOffset / triggerThresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { animatedOffset.coerceAtLeast(44.dp.toPx()).toDp() })
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            rotationZ = if (isRefreshing) 0f else progress * 360f
                            scaleX = if (isRefreshing) 1f else (0.4f + progress * 0.6f)
                            scaleY = if (isRefreshing) 1f else (0.4f + progress * 0.6f)
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Pull down to refresh",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
