package com.parthipan.colorclashcards.ui.splash

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    var fanOut by remember { mutableStateOf(false) }
    var showCCC by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    // Fan animations for 3 cards (-15, 0, +15 degrees)
    val cardRotationLeft by animateFloatAsState(
        targetValue = if (fanOut) -15f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_left_rotation"
    )
    val cardRotationRight by animateFloatAsState(
        targetValue = if (fanOut) 15f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_right_rotation"
    )
    val cardOffsetLeft by animateFloatAsState(
        targetValue = if (fanOut) -20f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_left_offset"
    )
    val cardOffsetRight by animateFloatAsState(
        targetValue = if (fanOut) 20f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_right_offset"
    )

    // Logo area: scale from 0.6 to 1.0 with bouncy spring + fade in
    val logoScale by animateFloatAsState(
        targetValue = if (fanOut) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (fanOut) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "logo_alpha"
    )

    // CCC text fades in after fan completes
    val cccAlpha by animateFloatAsState(
        targetValue = if (showCCC) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ccc_alpha"
    )

    // Title: slide up from 20dp + fade in
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "title_alpha"
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (showTitle) 0f else 20f,
        animationSpec = tween(durationMillis = 500),
        label = "title_offset"
    )

    // Subtitle: slide up + fade in
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (showSubtitle) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "subtitle_alpha"
    )
    val subtitleOffset by animateFloatAsState(
        targetValue = if (showSubtitle) 0f else 20f,
        animationSpec = tween(durationMillis = 500),
        label = "subtitle_offset"
    )

    LaunchedEffect(key1 = true) {
        fanOut = true
        delay(500)
        showCCC = true
        delay(200)
        showTitle = true
        delay(200)
        showSubtitle = true
        delay(1100)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CardRed,
                        CardYellow,
                        CardGreen,
                        CardBlue
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Fanning card stack
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Red card (left, fans to -15 degrees)
                Box(
                    modifier = Modifier
                        .size(70.dp, 100.dp)
                        .graphicsLayer {
                            rotationZ = cardRotationLeft
                            translationX = cardOffsetLeft
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .background(
                            CardRed,
                            RoundedCornerShape(8.dp)
                        )
                )

                // Green card (center, stays at 0 degrees)
                Box(
                    modifier = Modifier
                        .size(70.dp, 100.dp)
                        .background(
                            CardGreen,
                            RoundedCornerShape(8.dp)
                        )
                )

                // Blue card (right, fans to +15 degrees)
                Box(
                    modifier = Modifier
                        .size(70.dp, 100.dp)
                        .graphicsLayer {
                            rotationZ = cardRotationRight
                            translationX = cardOffsetRight
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .background(
                            CardBlue,
                            RoundedCornerShape(8.dp)
                        )
                )

                // "CCC" text fades in on top after fan
                Text(
                    text = "CCC",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.alpha(cccAlpha)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Color Clash Cards",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffset.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Match colors. Play cards. Win!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier
                    .alpha(subtitleAlpha)
                    .offset(y = subtitleOffset.dp)
            )
        }
    }
}
