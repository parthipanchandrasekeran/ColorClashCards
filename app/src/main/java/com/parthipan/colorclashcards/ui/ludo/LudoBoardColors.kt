package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.ui.graphics.Color
import com.parthipan.colorclashcards.game.ludo.model.LudoColor

/**
 * Color definitions for the Ludo board UI.
 */
object LudoBoardColors {
    // Player colors - vivid for home areas and tokens
    val Red = Color(0xFFE53935)
    val RedLight = Color(0xFFFFCDD2)
    val RedDark = Color(0xFFC62828)

    val Blue = Color(0xFF1E88E5)
    val BlueLight = Color(0xFFBBDEFB)
    val BlueDark = Color(0xFF1565C0)

    val Green = Color(0xFF43A047)
    val GreenLight = Color(0xFFC8E6C9)
    val GreenDark = Color(0xFF2E7D32)

    val Yellow = Color(0xFFFFB300)
    val YellowLight = Color(0xFFFFECB3)
    val YellowDark = Color(0xFFFF8F00)

    // Board colors
    val BoardBackground = Color(0xFFFFF8E1)
    val TrackWhite = Color(0xFFFFFFFF)
    val TrackBorder = Color(0xFF424242)
    val SafeCell = Color(0xFFE0E0E0)
    val CenterTriangle = Color(0xFF9E9E9E)

    // Token colors
    val TokenBorder = Color(0xFF212121)
    val TokenHighlight = Color(0xFFFFD54F)
    val TokenShadow = Color(0x40000000)

    // Badge colors
    val FinishedBadge = Color(0xFF4CAF50)

    /**
     * Get the primary color for a LudoColor.
     */
    fun getColor(ludoColor: LudoColor): Color {
        return when (ludoColor) {
            LudoColor.RED -> Red
            LudoColor.BLUE -> Blue
            LudoColor.GREEN -> Green
            LudoColor.YELLOW -> Yellow
        }
    }

    /**
     * Get the light variant color for a LudoColor.
     */
    fun getLightColor(ludoColor: LudoColor): Color {
        return when (ludoColor) {
            LudoColor.RED -> RedLight
            LudoColor.BLUE -> BlueLight
            LudoColor.GREEN -> GreenLight
            LudoColor.YELLOW -> YellowLight
        }
    }

    /**
     * Get the dark variant color for a LudoColor.
     */
    fun getDarkColor(ludoColor: LudoColor): Color {
        return when (ludoColor) {
            LudoColor.RED -> RedDark
            LudoColor.BLUE -> BlueDark
            LudoColor.GREEN -> GreenDark
            LudoColor.YELLOW -> YellowDark
        }
    }
}
