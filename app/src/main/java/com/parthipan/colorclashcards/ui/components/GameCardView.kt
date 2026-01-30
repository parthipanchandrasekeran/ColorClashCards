package com.parthipan.colorclashcards.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parthipan.colorclashcards.game.model.Card as GameCard
import com.parthipan.colorclashcards.game.model.CardColor
import com.parthipan.colorclashcards.game.model.CardType
import kotlin.random.Random

// Define card colors locally for preview compatibility
private val CardRed = Color(0xFFE53935)
private val CardGreen = Color(0xFF43A047)
private val CardBlue = Color(0xFF1E88E5)
private val CardYellow = Color(0xFFFFD600)

/**
 * Original card design with diagonal stripe, centered badge, and corner labels.
 * Inspired by classic card games but with unique visual identity.
 *
 * @param card The card data to display
 * @param modifier Modifier for sizing (use Modifier.size() or Modifier.width())
 * @param faceDown Whether to show the card back
 * @param isPlayable Whether this card can be played (shows glow effect)
 * @param onClick Optional click handler (null = not clickable)
 */
@Composable
fun GameCardView(
    card: GameCard,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    isPlayable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val baseColor = card.color.toCardColor()
    val isWild = card.type.isWild()
    val textColor = if (card.color == CardColor.YELLOW) Color.Black else Color.White

    // Press animation state
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cardScale"
    )

    val cornerRadius = 14.dp

    Card(
        modifier = modifier
            .aspectRatio(2.5f / 3.5f) // Standard playing card ratio
            .scale(scale)
            .shadow(
                elevation = if (isPlayable) 12.dp else 4.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (isPlayable) baseColor else Color.Black,
                spotColor = if (isPlayable) baseColor else Color.Black
            )
            .then(
                if (isPlayable) {
                    Modifier.border(
                        width = 3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                Color.White.copy(alpha = 0.7f),
                                Color.White
                            )
                        ),
                        shape = RoundedCornerShape(cornerRadius)
                    )
                } else Modifier
            )
            .pointerInput(faceDown, onClick) {
                if (!faceDown && onClick != null) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
                }
            },
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (faceDown) {
            CardBack(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = cornerRadius
            )
        } else {
            CardFront(
                card = card,
                baseColor = baseColor,
                textColor = textColor,
                isWild = isWild,
                cornerRadius = cornerRadius,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CardBack(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF1A1A2E)
                    )
                )
            )
            .drawBehind {
                // Draw diagonal stripe pattern
                val stripeWidth = 4.dp.toPx()
                val gap = 8.dp.toPx()
                var x = -this.size.height
                while (x < this.size.width + this.size.height) {
                    drawLine(
                        color = Color(0xFF2A2A4A),
                        start = Offset(x, 0f),
                        end = Offset(x + this.size.height, this.size.height),
                        strokeWidth = stripeWidth
                    )
                    x += stripeWidth + gap
                }

                // Draw border glow
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            CardRed.copy(alpha = 0.3f),
                            CardBlue.copy(alpha = 0.3f),
                            CardGreen.copy(alpha = 0.3f),
                            CardYellow.copy(alpha = 0.3f)
                        )
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // CC Logo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "CC",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-2).sp
            )
            Text(
                text = "CLASH",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun CardFront(
    card: GameCard,
    baseColor: Color,
    textColor: Color,
    isWild: Boolean,
    cornerRadius: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    // Deterministic seed from card id for noise pattern
    val patternSeed = remember(card.id) { card.id.hashCode().toLong() }
    val innerBorderColor = if (isWild) Color.White.copy(alpha = 0.15f)
                           else baseColor.lighten(0.3f).copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (isWild) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2D2D2D),
                            Color(0xFF1F1F1F),
                            Color(0xFF2D2D2D)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            baseColor,
                            baseColor.copy(alpha = 0.85f),
                            baseColor
                        )
                    )
                }
            )
            .drawBehind {
                // Draw the diagonal flash stripe
                drawFlashStripe(isWild, baseColor)

                // Draw subtle noise/pattern (deterministic from card id)
                drawCardNoisePattern(patternSeed, isWild)

                // Draw thin inner border (1dp lighter shade)
                val innerBorderWidth = 1.dp.toPx()
                val inset = innerBorderWidth / 2 + 2.dp.toPx()
                drawRoundRect(
                    color = innerBorderColor,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                    cornerRadius = CornerRadius((cornerRadius.toPx() - inset).coerceAtLeast(0f)),
                    style = Stroke(width = innerBorderWidth)
                )
            }
    ) {
        // Corner labels
        CornerLabel(
            card = card,
            textColor = textColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 4.dp, start = 6.dp)
        )

        CornerLabel(
            card = card,
            textColor = textColor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 4.dp, end = 6.dp)
                .rotate(180f)
        )

        // Center badge
        CenterBadge(
            card = card,
            baseColor = baseColor,
            textColor = textColor,
            isWild = isWild,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * Draw subtle noise pattern on card surface for texture.
 * Pattern is deterministic based on seed for consistent appearance.
 * Optimized: Reduced number of draw operations.
 */
private fun DrawScope.drawCardNoisePattern(seed: Long, isWild: Boolean) {
    val random = Random(seed)
    val patternAlpha = if (isWild) 0.03f else 0.06f
    val patternColor = Color.White.copy(alpha = patternAlpha)
    val darkPatternColor = Color.Black.copy(alpha = 0.04f)

    // Draw fewer circles for better performance
    repeat(8) { i ->
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val radius = random.nextFloat() * 3f + 1f

        drawCircle(
            color = if (i % 3 != 0) patternColor else darkPatternColor,
            radius = radius,
            center = Offset(x, y)
        )
    }

    // Draw fewer lines
    repeat(4) {
        val startX = random.nextFloat() * size.width
        val startY = random.nextFloat() * size.height
        val endX = startX + (random.nextFloat() - 0.5f) * 15f
        val endY = startY + (random.nextFloat() - 0.5f) * 15f

        drawLine(
            color = patternColor,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 0.5f
        )
    }
}

private fun DrawScope.drawFlashStripe(isWild: Boolean, baseColor: Color) {
    val stripeWidth = size.width * 0.35f
    val stripeColor = if (isWild) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.15f)
    }

    rotate(degrees = -25f, pivot = center) {
        drawRect(
            color = stripeColor,
            topLeft = Offset(center.x - stripeWidth / 2, -size.height),
            size = Size(stripeWidth, size.height * 3)
        )
    }

    // Add subtle edge highlight
    if (!isWild) {
        val highlightPath = Path().apply {
            moveTo(0f, size.height * 0.3f)
            lineTo(size.width * 0.3f, 0f)
            lineTo(size.width * 0.4f, 0f)
            lineTo(0f, size.height * 0.4f)
            close()
        }
        drawPath(
            path = highlightPath,
            color = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun CornerLabel(
    card: GameCard,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val displayText = when (card.type) {
        CardType.NUMBER -> card.number.toString()
        CardType.SKIP -> "⊘"
        CardType.REVERSE -> "⟳"
        CardType.DRAW_TWO -> "+2"
        CardType.WILD_COLOR -> "W"
        CardType.WILD_DRAW_FOUR -> "+4"
    }

    Text(
        text = displayText,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
private fun CenterBadge(
    card: GameCard,
    baseColor: Color,
    textColor: Color,
    isWild: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeCornerRadius = 12.dp

    // Scrim color for text readability
    val scrimColor = if (isWild) Color.Black.copy(alpha = 0.3f)
                     else baseColor.darken(0.3f).copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxSize(0.65f)
            .shadow(6.dp, RoundedCornerShape(badgeCornerRadius))
            .clip(RoundedCornerShape(badgeCornerRadius))
            .background(
                if (isWild) {
                    Brush.sweepGradient(
                        colors = listOf(CardRed, CardYellow, CardGreen, CardBlue, CardRed)
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(baseColor.copy(alpha = 0.95f), baseColor.darken(0.15f))
                    )
                }
            )
            .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(badgeCornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .clip(RoundedCornerShape(badgeCornerRadius * 0.8f))
                .background(scrimColor),
            contentAlignment = Alignment.Center
        ) {
            when (card.type) {
                CardType.NUMBER -> NumberContent(card.number ?: 0, if (isWild) Color.White else textColor)
                CardType.SKIP -> SkipIcon(if (isWild) Color.White else textColor)
                CardType.REVERSE -> ReverseIcon(if (isWild) Color.White else textColor)
                CardType.DRAW_TWO -> DrawTwoIcon(if (isWild) Color.White else textColor)
                CardType.WILD_COLOR -> WildIcon()
                CardType.WILD_DRAW_FOUR -> WildDrawFourIcon()
            }
        }
    }
}

@Composable
private fun NumberContent(number: Int, textColor: Color) {
    Text(
        text = number.toString(),
        color = textColor,
        fontSize = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SkipIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize(0.6f)) {
        val strokeWidth = 4.dp.toPx()
        val radius = size.minDimension / 2 - strokeWidth

        drawCircle(color = color, radius = radius, style = Stroke(width = strokeWidth))
        drawLine(
            color = color,
            start = Offset(center.x - radius * 0.7f, center.y + radius * 0.7f),
            end = Offset(center.x + radius * 0.7f, center.y - radius * 0.7f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ReverseIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize(0.6f)) {
        val strokeWidth = 3.dp.toPx()
        val arrowSize = size.minDimension * 0.2f

        val path = Path().apply {
            moveTo(center.x + size.width * 0.3f, center.y - size.height * 0.15f)
            quadraticTo(
                center.x + size.width * 0.1f, center.y - size.height * 0.35f,
                center.x - size.width * 0.2f, center.y - size.height * 0.15f
            )
        }
        val path2 = Path().apply {
            moveTo(center.x - size.width * 0.3f, center.y + size.height * 0.15f)
            quadraticTo(
                center.x - size.width * 0.1f, center.y + size.height * 0.35f,
                center.x + size.width * 0.2f, center.y + size.height * 0.15f
            )
        }

        drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        drawPath(path2, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

        val topArrowPath = Path().apply {
            moveTo(center.x - size.width * 0.2f, center.y - size.height * 0.15f)
            lineTo(center.x - size.width * 0.2f - arrowSize * 0.6f, center.y - size.height * 0.15f - arrowSize)
            lineTo(center.x - size.width * 0.2f + arrowSize * 0.6f, center.y - size.height * 0.15f - arrowSize)
            close()
        }
        val bottomArrowPath = Path().apply {
            moveTo(center.x + size.width * 0.2f, center.y + size.height * 0.15f)
            lineTo(center.x + size.width * 0.2f - arrowSize * 0.6f, center.y + size.height * 0.15f + arrowSize)
            lineTo(center.x + size.width * 0.2f + arrowSize * 0.6f, center.y + size.height * 0.15f + arrowSize)
            close()
        }

        drawPath(topArrowPath, color, style = Fill)
        drawPath(bottomArrowPath, color, style = Fill)
    }
}

@Composable
private fun DrawTwoIcon(color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(14.dp, 18.dp)
                    .offset(x = (-3).dp, y = (-2).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.5f))
                    .border(1.dp, color, RoundedCornerShape(3.dp))
            )
            Box(
                modifier = Modifier
                    .size(14.dp, 18.dp)
                    .offset(x = 3.dp, y = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.7f))
                    .border(1.dp, color, RoundedCornerShape(3.dp))
            )
        }
        Text(text = "+2", color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun WildIcon() {
    val squareSize = 14.dp
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(squareSize).clip(RoundedCornerShape(3.dp)).background(CardRed))
            Box(Modifier.size(squareSize).clip(RoundedCornerShape(3.dp)).background(CardBlue))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(squareSize).clip(RoundedCornerShape(3.dp)).background(CardYellow))
            Box(Modifier.size(squareSize).clip(RoundedCornerShape(3.dp)).background(CardGreen))
        }
    }
}

@Composable
private fun WildDrawFourIcon() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        WildIcon()
        Text(text = "+4", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Extension function to darken a color.
 */
private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Extension function to lighten a color.
 */
private fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red + (1f - red) * factor).coerceIn(0f, 1f),
        green = (green + (1f - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Extension to convert CardColor to Compose Color.
 */
private fun CardColor.toCardColor(): Color {
    return when (this) {
        CardColor.RED -> CardRed
        CardColor.GREEN -> CardGreen
        CardColor.BLUE -> CardBlue
        CardColor.YELLOW -> CardYellow
        CardColor.WILD -> Color(0xFF2D2D2D)
    }
}

// ==================== Preview Composables ====================

/**
 * Preview helper to create cards without runtime dependencies.
 */
private object PreviewCards {
    fun numberCard(color: CardColor, number: Int) = GameCard(
        id = "preview-$color-$number",
        color = color,
        type = CardType.NUMBER,
        number = number
    )

    fun skipCard(color: CardColor) = GameCard(
        id = "preview-skip-$color",
        color = color,
        type = CardType.SKIP
    )

    fun reverseCard(color: CardColor) = GameCard(
        id = "preview-reverse-$color",
        color = color,
        type = CardType.REVERSE
    )

    fun drawTwoCard(color: CardColor) = GameCard(
        id = "preview-draw2-$color",
        color = color,
        type = CardType.DRAW_TWO
    )

    fun wildCard() = GameCard(
        id = "preview-wild",
        color = CardColor.WILD,
        type = CardType.WILD_COLOR
    )

    fun wildDrawFourCard() = GameCard(
        id = "preview-wild4",
        color = CardColor.WILD,
        type = CardType.WILD_DRAW_FOUR
    )
}

@Preview(name = "Number Cards - All Colors", showBackground = true, backgroundColor = 0xFF1B4D3E)
@Composable
private fun PreviewNumberCards() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameCardView(card = PreviewCards.numberCard(CardColor.RED, 7), modifier = Modifier.width(70.dp))
        GameCardView(card = PreviewCards.numberCard(CardColor.BLUE, 3), modifier = Modifier.width(70.dp))
        GameCardView(card = PreviewCards.numberCard(CardColor.GREEN, 5), modifier = Modifier.width(70.dp))
        GameCardView(card = PreviewCards.numberCard(CardColor.YELLOW, 9), modifier = Modifier.width(70.dp))
    }
}

@Preview(name = "Action Cards", showBackground = true, backgroundColor = 0xFF1B4D3E)
@Composable
private fun PreviewActionCards() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameCardView(card = PreviewCards.skipCard(CardColor.RED), modifier = Modifier.width(70.dp))
        GameCardView(card = PreviewCards.reverseCard(CardColor.BLUE), modifier = Modifier.width(70.dp))
        GameCardView(card = PreviewCards.drawTwoCard(CardColor.GREEN), modifier = Modifier.width(70.dp))
        GameCardView(card = PreviewCards.drawTwoCard(CardColor.YELLOW), modifier = Modifier.width(70.dp))
    }
}

@Preview(name = "Wild Cards", showBackground = true, backgroundColor = 0xFF1B4D3E)
@Composable
private fun PreviewWildCards() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GameCardView(card = PreviewCards.wildCard(), modifier = Modifier.width(80.dp))
        GameCardView(card = PreviewCards.wildDrawFourCard(), modifier = Modifier.width(80.dp))
    }
}

@Preview(name = "Face Down Card", showBackground = true, backgroundColor = 0xFF1B4D3E)
@Composable
private fun PreviewFaceDownCard() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GameCardView(card = PreviewCards.numberCard(CardColor.RED, 0), modifier = Modifier.width(60.dp), faceDown = true)
        GameCardView(card = PreviewCards.numberCard(CardColor.BLUE, 0), modifier = Modifier.width(80.dp), faceDown = true)
    }
}

@Preview(name = "Playable Card (Glowing)", showBackground = true, backgroundColor = 0xFF1B4D3E)
@Composable
private fun PreviewPlayableCard() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GameCardView(card = PreviewCards.numberCard(CardColor.RED, 5), modifier = Modifier.width(70.dp), isPlayable = false)
        GameCardView(card = PreviewCards.numberCard(CardColor.RED, 5), modifier = Modifier.width(70.dp), isPlayable = true)
    }
}

@Preview(name = "Card Sizes", showBackground = true, backgroundColor = 0xFF1B4D3E)
@Composable
private fun PreviewCardSizes() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        GameCardView(card = PreviewCards.numberCard(CardColor.BLUE, 7), modifier = Modifier.width(50.dp))
        GameCardView(card = PreviewCards.numberCard(CardColor.BLUE, 7), modifier = Modifier.width(70.dp))
        GameCardView(card = PreviewCards.numberCard(CardColor.BLUE, 7), modifier = Modifier.width(90.dp))
        GameCardView(card = PreviewCards.numberCard(CardColor.BLUE, 7), modifier = Modifier.width(110.dp))
    }
}
