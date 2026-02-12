package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parthipan.colorclashcards.ui.components.pulsingBorder
import com.parthipan.colorclashcards.ui.theme.Gold
import android.util.Log
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.LudoPlayer
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState

/**
 * Calculate offsets for stacked tokens within a cell.
 * Returns a list of (x, y) offsets and a scale factor.
 *
 * Layout patterns:
 * - 1 token: centered, full size
 * - 2 tokens: side by side horizontally
 * - 3 tokens: triangle arrangement
 * - 4+ tokens: 2x2 micro-grid
 */
fun calculateStackOffsets(count: Int, cellSize: Dp): Pair<List<Pair<Dp, Dp>>, Float> {
    val quarterCell = cellSize.value / 4

    return when (count) {
        1 -> Pair(
            listOf(Pair(0.dp, 0.dp)),
            1f
        )
        2 -> Pair(
            listOf(
                Pair((-quarterCell * 0.6f).dp, 0.dp),  // Left
                Pair((quarterCell * 0.6f).dp, 0.dp)    // Right
            ),
            0.75f
        )
        3 -> Pair(
            listOf(
                Pair(0.dp, (-quarterCell * 0.5f).dp),           // Top center
                Pair((-quarterCell * 0.6f).dp, (quarterCell * 0.4f).dp),  // Bottom left
                Pair((quarterCell * 0.6f).dp, (quarterCell * 0.4f).dp)    // Bottom right
            ),
            0.65f
        )
        else -> Pair(
            // 4+ tokens: 2x2 grid (only use first 4 positions)
            listOf(
                Pair((-quarterCell * 0.5f).dp, (-quarterCell * 0.5f).dp),  // Top-left
                Pair((quarterCell * 0.5f).dp, (-quarterCell * 0.5f).dp),   // Top-right
                Pair((-quarterCell * 0.5f).dp, (quarterCell * 0.5f).dp),   // Bottom-left
                Pair((quarterCell * 0.5f).dp, (quarterCell * 0.5f).dp)     // Bottom-right
            ),
            0.55f
        )
    }
}

/**
 * Get the board position for a token based on its state and color.
 */
fun getTokenBoardPosition(token: Token, color: LudoColor): BoardPosition? {
    val pos = when (token.state) {
        TokenState.HOME -> {
            LudoBoardPositions.getHomeBasePositions(color).getOrNull(token.id)
        }
        TokenState.ACTIVE -> {
            LudoBoardPositions.getGridPosition(token.position, color)
        }
        TokenState.FINISHED -> {
            LudoBoardPositions.getFinishPosition(color)
        }
    }
    if (pos == null && token.state == TokenState.ACTIVE) {
        Log.w("LudoDebug", "RENDER WARNING: $color token#${token.id} ACTIVE at " +
            "position=${token.position} has NO grid position — rendering will skip this token")
    }
    return pos
}

/**
 * Compact player chip showing color, name, and token counts.
 * Shared between offline and online game screens.
 *
 * @param isDisconnected Whether the player is disconnected (online only)
 */
@Composable
fun SharedPlayerChip(
    player: LudoPlayer,
    displayName: String,
    isCurrentTurn: Boolean,
    isDisconnected: Boolean = false,
    isLeading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playerColor = LudoBoardColors.getColor(player.color)
    val homeCount = player.tokens.count { it.state == TokenState.HOME }
    val activeCount = player.tokens.count { it.state == TokenState.ACTIVE }
    val finishedCount = player.tokens.count { it.state == TokenState.FINISHED }

    // Subtle scale pulse for current player
    val pulseTransition = rememberInfiniteTransition(label = "chip_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCurrentTurn) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chip_scale"
    )

    Card(
        modifier = modifier
            .scale(pulseScale)
            .pulsingBorder(
                color = playerColor,
                enabled = isCurrentTurn,
                cornerRadius = 12.dp
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDisconnected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                isCurrentTurn -> playerColor.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentTurn) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Turn indicator + Color dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isDisconnected) Color.Gray else playerColor)
                    .then(
                        if (isCurrentTurn) {
                            Modifier
                                .border(2.dp, Color.White, CircleShape)
                                .testTag("turnIndicator_${player.id}")
                        } else Modifier
                    )
            )

            // Name
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Normal,
                color = if (isDisconnected) Color.Gray else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            // Leading player star
            if (isLeading && finishedCount > 0) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Leading",
                    modifier = Modifier.size(12.dp),
                    tint = Gold
                )
            }

            // Disconnected icon
            if (isDisconnected) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Disconnected",
                    modifier = Modifier
                        .size(12.dp)
                        .testTag("disconnectedIcon_${player.id}"),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            // Token counts: H/A/F
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SharedTokenCountBadge(
                    count = homeCount,
                    color = playerColor.copy(alpha = 0.4f)
                )
                SharedTokenCountBadge(
                    count = activeCount,
                    color = playerColor
                )
                SharedTokenCountBadge(
                    count = finishedCount,
                    color = LudoBoardColors.FinishedBadge
                )
            }
        }
    }
}

/**
 * Small badge showing token count.
 */
@Composable
fun SharedTokenCountBadge(
    count: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
