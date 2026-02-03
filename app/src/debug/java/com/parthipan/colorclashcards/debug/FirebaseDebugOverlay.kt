package com.parthipan.colorclashcards.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parthipan.colorclashcards.util.FirebaseListenerAggregator
import com.parthipan.colorclashcards.util.ListenerStats
import com.parthipan.colorclashcards.util.ListenerSummary
import kotlinx.coroutines.delay

/**
 * Debug overlay for monitoring Firebase listener activity in real-time.
 *
 * Usage in a debug screen:
 * ```kotlin
 * Box {
 *     // Your screen content
 *     FirebaseDebugOverlay(
 *         modifier = Modifier.align(Alignment.TopEnd)
 *     )
 * }
 * ```
 *
 * Only included in debug builds.
 */
@Composable
fun FirebaseDebugOverlay(
    modifier: Modifier = Modifier,
    aggregator: FirebaseListenerAggregator = FirebaseListenerAggregator.instance,
    refreshIntervalMs: Long = 1000,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var summary by remember { mutableStateOf<ListenerSummary?>(null) }

    // Auto-refresh stats
    LaunchedEffect(Unit) {
        while (true) {
            summary = aggregator.getSummary()
            delay(refreshIntervalMs)
        }
    }

    Column(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (summary?.healthStatus) {
                ListenerSummary.HealthStatus.HEALTHY -> Color.Green
                ListenerSummary.HealthStatus.CAUTION -> Color.Yellow
                ListenerSummary.HealthStatus.WARNING -> Color(0xFFFFA500) // Orange
                ListenerSummary.HealthStatus.CRITICAL -> Color.Red
                null -> Color.Gray
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Firebase",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.width(8.dp))

            summary?.let { s ->
                Text(
                    text = "${s.totalUpdates} updates",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Refresh button
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { summary = aggregator.getSummary() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Expand/collapse indicator
            Text(
                text = if (isExpanded) "▲" else "▼",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }

        // Expanded details
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                summary?.let { s ->
                    // Summary stats
                    SummaryRow("Listeners", s.totalListeners.toString())
                    SummaryRow("Total Updates", s.totalUpdates.toString())
                    SummaryRow("Excessive", s.totalExcessiveUpdates.toString(),
                        valueColor = if (s.totalExcessiveUpdates > 0) Color.Red else Color.Green)
                    SummaryRow("Payload", "${s.totalPayloadBytes / 1024}KB")

                    Spacer(modifier = Modifier.height(8.dp))

                    // Individual listeners
                    Text(
                        text = "Listeners:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    s.stats.forEach { stats ->
                        ListenerRow(stats)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reset button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable {
                                aggregator.resetAll()
                                summary = aggregator.getSummary()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Reset",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Stats",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } ?: run {
                    Text(
                        text = "No data",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ListenerRow(stats: ListenerStats) {
    val statusColor = when {
        stats.excessiveUpdateCount > 10 -> Color.Red
        stats.hasExcessiveUpdates -> Color.Yellow
        else -> Color.Green
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(statusColor)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = stats.name,
            color = Color.White,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${stats.totalUpdates}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )

        if (stats.excessiveUpdateCount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "(${stats.excessiveUpdateCount}!)",
                color = Color.Red,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Minimal badge showing Firebase health status.
 * Useful for showing in a corner without taking much space.
 */
@Composable
fun FirebaseHealthBadge(
    modifier: Modifier = Modifier,
    aggregator: FirebaseListenerAggregator = FirebaseListenerAggregator.instance,
    onClick: () -> Unit = {}
) {
    var summary by remember { mutableStateOf<ListenerSummary?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            summary = aggregator.getSummary()
            delay(2000)
        }
    }

    val statusColor = when (summary?.healthStatus) {
        ListenerSummary.HealthStatus.HEALTHY -> Color.Green
        ListenerSummary.HealthStatus.CAUTION -> Color.Yellow
        ListenerSummary.HealthStatus.WARNING -> Color(0xFFFFA500)
        ListenerSummary.HealthStatus.CRITICAL -> Color.Red
        null -> Color.Gray
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor)
            )

            summary?.let { s ->
                if (s.totalExcessiveUpdates > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${s.totalExcessiveUpdates}",
                        color = Color.Red,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
