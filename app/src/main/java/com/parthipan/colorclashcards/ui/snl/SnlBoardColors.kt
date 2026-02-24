package com.parthipan.colorclashcards.ui.snl

import androidx.compose.ui.graphics.Color

/**
 * Color definitions for the Snake & Ladder board UI.
 */
object SnlBoardColors {
    // Player colors (indexed 0-3: Red, Blue, Green, Yellow)
    val PlayerColors = listOf(
        Color(0xFFE53935), // Red
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFFFB300)  // Yellow
    )

    val PlayerColorsDark = listOf(
        Color(0xFFC62828),
        Color(0xFF1565C0),
        Color(0xFF2E7D32),
        Color(0xFFFF8F00)
    )

    val PlayerColorsLight = listOf(
        Color(0xFFFFCDD2),
        Color(0xFFBBDEFB),
        Color(0xFFC8E6C9),
        Color(0xFFFFECB3)
    )

    // Board colors
    val BoardBackground = Color(0xFFFFF8E1) // Warm cream
    val SquareLight = Color(0xFFFFF3E0) // Light orange-cream
    val SquareDark = Color(0xFFFFE0B2) // Darker orange-cream
    val SquareNumber = Color(0xFF5D4037) // Brown text
    val GridLine = Color(0xFFBCAAA4) // Light brown grid

    // Snake colors
    val SnakeBody = Color(0xFFE53935) // Red
    val SnakeBodyDark = Color(0xFFC62828)
    val SnakeEye = Color(0xFFFFEB3B)

    // Ladder colors
    val LadderRail = Color(0xFF43A047) // Green
    val LadderRailDark = Color(0xFF2E7D32)
    val LadderRung = Color(0xFF66BB6A)

    // Token
    val TokenBorder = Color(0xFF212121)
    val TokenShadow = Color(0x40000000)

    // Special squares
    val Square100 = Color(0xFFFDD835) // Gold for winning square
    val Square1 = Color(0xFFA5D6A7) // Light green for start

    // Theme accents
    val Primary = Color(0xFF43A047) // Green
    val PrimaryDark = Color(0xFF2E7D32)
    val Secondary = Color(0xFFE53935) // Red (snake)
    val Accent = Color(0xFFFFB300) // Yellow

    fun getPlayerColor(colorIndex: Int): Color =
        PlayerColors.getOrElse(colorIndex) { PlayerColors[0] }

    fun getPlayerColorDark(colorIndex: Int): Color =
        PlayerColorsDark.getOrElse(colorIndex) { PlayerColorsDark[0] }

    fun getPlayerColorLight(colorIndex: Int): Color =
        PlayerColorsLight.getOrElse(colorIndex) { PlayerColorsLight[0] }
}
