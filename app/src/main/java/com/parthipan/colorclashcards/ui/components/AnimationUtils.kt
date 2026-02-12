package com.parthipan.colorclashcards.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Draws faint, slowly drifting shapes behind content.
 *
 * @param colors List of 4 colors for the shapes
 * @param shapeType "circle" or "rect" (card-like rounded rectangles)
 */
fun Modifier.floatingShapes(
    colors: List<Color>,
    shapeType: String = "rect"
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "floating_shapes")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shape_drift"
    )

    val c1 = colors.getOrElse(0) { Color.Red }.copy(alpha = 0.04f)
    val c2 = colors.getOrElse(1) { Color.Blue }.copy(alpha = 0.04f)
    val c3 = colors.getOrElse(2) { Color.Green }.copy(alpha = 0.04f)
    val c4 = colors.getOrElse(3) { Color.Yellow }.copy(alpha = 0.04f)

    drawBehind {
        val driftOffset = drift * 20f
        val positions = listOf(
            Triple(Offset(size.width * 0.05f, size.height * 0.08f + driftOffset), 15f + driftOffset * 0.3f, c1),
            Triple(Offset(size.width * 0.8f, size.height * 0.2f - driftOffset * 0.5f), -10f - driftOffset * 0.2f, c2),
            Triple(Offset(size.width * 0.65f, size.height * 0.65f + driftOffset * 0.7f), 25f - driftOffset * 0.4f, c3),
            Triple(Offset(size.width * 0.15f, size.height * 0.7f - driftOffset * 0.3f), -20f + driftOffset * 0.5f, c4)
        )
        val sizes = listOf(
            Size(60f, if (shapeType == "circle") 60f else 90f),
            Size(50f, if (shapeType == "circle") 50f else 75f),
            Size(55f, if (shapeType == "circle") 55f else 80f),
            Size(45f, if (shapeType == "circle") 45f else 65f)
        )

        positions.forEachIndexed { index, (offset, angle, color) ->
            val s = sizes[index]
            val pivotX = offset.x + s.width / 2
            val pivotY = offset.y + s.height / 2
            rotate(degrees = angle, pivot = Offset(pivotX, pivotY)) {
                if (shapeType == "circle") {
                    drawCircle(
                        color = color,
                        radius = s.width / 2,
                        center = Offset(pivotX, pivotY)
                    )
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = offset,
                        size = s,
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
        }
    }
}

/**
 * Gradient-filled button with spring press animation.
 */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "btn_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = gradientColors.first().copy(alpha = 0.3f),
                spotColor = gradientColors.first().copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(gradientColors)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Wrapper that animates children in with a staggered fade+slide based on index.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    delayPerItem: Long = 100L,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * delayPerItem)
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "stagger_alpha_$index"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "stagger_offset_$index"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        }
    ) {
        content()
    }
}

/**
 * Animated pulsing border for drawing attention (e.g. current player, rejoin card).
 *
 * @param color The border color
 * @param enabled Whether the pulsing is active
 */
fun Modifier.pulsingBorder(
    color: Color,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    baseWidth: Dp = 2.dp
): Modifier = composed {
    if (!enabled) return@composed this

    val transition = rememberInfiniteTransition(label = "pulsing_border")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseWidth by transition.animateFloat(
        initialValue = baseWidth.value,
        targetValue = baseWidth.value + 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_width"
    )

    drawWithContent {
        drawContent()
        val cr = cornerRadius.toPx()
        drawRoundRect(
            color = color.copy(alpha = pulseAlpha),
            cornerRadius = CornerRadius(cr, cr),
            style = Stroke(width = pulseWidth)
        )
    }
}
