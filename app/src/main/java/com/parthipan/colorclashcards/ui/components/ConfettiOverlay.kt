package com.parthipan.colorclashcards.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var color: Color,
    var size: Float,
    var alpha: Float
)

private val confettiColors = listOf(
    CardRed,
    CardBlue,
    CardGreen,
    CardYellow,
    Color.White
)

/**
 * Canvas-based confetti particle overlay. ~50 particles with gravity, rotation, drift.
 * Duration ~2.5s, then disappears.
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    if (!trigger) return

    val random = remember { Random(System.currentTimeMillis()) }
    val particles = remember {
        List(50) { createParticle(random) }.toMutableList()
    }
    var frameTime by remember { mutableLongStateOf(0L) }
    var isActive by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 2500L) {
                isActive = false
                break
            }
            frameTime = elapsed
            // Update particles
            for (p in particles) {
                p.x += p.vx
                p.y += p.vy
                p.vy += 0.15f // gravity
                p.rotation += p.rotationSpeed
                // Fade after 1.5s
                if (elapsed > 1500L) {
                    p.alpha = (1f - (elapsed - 1500f) / 1000f).coerceAtLeast(0f)
                }
            }
            delay(16L)
        }
    }

    if (!isActive) return

    Canvas(modifier = modifier.fillMaxSize()) {
        for (p in particles) {
            if (p.alpha <= 0f) continue
            rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = p.color.copy(alpha = p.alpha),
                    topLeft = Offset(p.x - p.size / 2, p.y - p.size / 2),
                    size = Size(p.size, p.size * 0.6f)
                )
            }
        }
    }
}

private fun createParticle(random: Random): Particle {
    return Particle(
        x = 540f + random.nextFloat() * 200f - 100f, // roughly center
        y = random.nextFloat() * 200f,
        vx = random.nextFloat() * 8f - 4f,
        vy = random.nextFloat() * -12f - 2f,
        rotation = random.nextFloat() * 360f,
        rotationSpeed = random.nextFloat() * 10f - 5f,
        color = confettiColors[random.nextInt(confettiColors.size)],
        size = random.nextFloat() * 10f + 6f,
        alpha = 1f
    )
}
