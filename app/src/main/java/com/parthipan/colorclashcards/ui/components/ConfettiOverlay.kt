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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow
import com.parthipan.colorclashcards.ui.theme.Gold
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class ParticleShape { RECT, CIRCLE, STAR, TRIANGLE }

private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var color: Color,
    var size: Float,
    var alpha: Float,
    var shape: ParticleShape,
    var windDrift: Float
)

private val defaultConfettiColors = listOf(
    CardRed,
    CardBlue,
    CardGreen,
    CardYellow,
    Gold,
    Color.White
)

/**
 * Canvas-based confetti particle overlay. ~80 particles with gravity, rotation, drift, wind.
 * Multiple shapes: rectangles, circles, stars, triangles.
 * Duration ~3s, then disappears.
 *
 * @param colors Optional custom colors for theming
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    colors: List<Color> = defaultConfettiColors
) {
    if (!trigger) return

    val random = remember { Random(System.currentTimeMillis()) }
    // We need to know actual canvas size for centering; initialize with 0 and update on first draw
    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }
    val particles = remember {
        mutableListOf<Particle>()
    }
    var initialized by remember { mutableStateOf(false) }
    var frameTime by remember { mutableLongStateOf(0L) }
    var isActive by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Wait for canvas size to be known
        while (canvasWidth == 0f) delay(16L)
        // Initialize particles with actual canvas dimensions
        if (!initialized) {
            val shapes = ParticleShape.entries.toTypedArray()
            repeat(80) {
                particles.add(
                    Particle(
                        x = canvasWidth / 2f + random.nextFloat() * 200f - 100f,
                        y = canvasHeight * 0.3f + random.nextFloat() * 200f - 100f,
                        vx = random.nextFloat() * 10f - 5f,
                        vy = random.nextFloat() * -14f - 2f,
                        rotation = random.nextFloat() * 360f,
                        rotationSpeed = random.nextFloat() * 12f - 6f,
                        color = colors[random.nextInt(colors.size)],
                        size = random.nextFloat() * 10f + 6f,
                        alpha = 1f,
                        shape = shapes[random.nextInt(shapes.size)],
                        windDrift = random.nextFloat() * 0.6f - 0.3f
                    )
                )
            }
            initialized = true
        }

        val startTime = System.currentTimeMillis()
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 3000L) {
                isActive = false
                break
            }
            frameTime = elapsed
            for (p in particles) {
                p.x += p.vx + p.windDrift
                p.y += p.vy
                p.vy += 0.15f // gravity
                p.vx += p.windDrift * 0.02f // accumulating wind
                p.rotation += p.rotationSpeed
                // Fade after 2s
                if (elapsed > 2000L) {
                    p.alpha = (1f - (elapsed - 2000f) / 1000f).coerceAtLeast(0f)
                }
            }
            delay(16L)
        }
    }

    if (!isActive) return

    Canvas(modifier = modifier.fillMaxSize()) {
        // Capture canvas size for particle init
        if (canvasWidth == 0f) {
            canvasWidth = size.width
            canvasHeight = size.height
        }

        for (p in particles) {
            if (p.alpha <= 0f) continue
            rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                val c = p.color.copy(alpha = p.alpha)
                when (p.shape) {
                    ParticleShape.RECT -> {
                        drawRect(
                            color = c,
                            topLeft = Offset(p.x - p.size / 2, p.y - p.size / 2),
                            size = Size(p.size, p.size * 0.6f)
                        )
                    }
                    ParticleShape.CIRCLE -> {
                        drawCircle(
                            color = c,
                            radius = p.size / 2,
                            center = Offset(p.x, p.y)
                        )
                    }
                    ParticleShape.STAR -> {
                        val path = Path()
                        val r = p.size / 2
                        val innerR = r * 0.4f
                        for (i in 0 until 5) {
                            val outerAngle = Math.toRadians((i * 72 - 90).toDouble())
                            val innerAngle = Math.toRadians((i * 72 + 36 - 90).toDouble())
                            val ox = p.x + r * cos(outerAngle).toFloat()
                            val oy = p.y + r * sin(outerAngle).toFloat()
                            val ix = p.x + innerR * cos(innerAngle).toFloat()
                            val iy = p.y + innerR * sin(innerAngle).toFloat()
                            if (i == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
                            path.lineTo(ix, iy)
                        }
                        path.close()
                        drawPath(path, color = c)
                    }
                    ParticleShape.TRIANGLE -> {
                        val path = Path()
                        val r = p.size / 2
                        path.moveTo(p.x, p.y - r)
                        path.lineTo(p.x - r * 0.87f, p.y + r * 0.5f)
                        path.lineTo(p.x + r * 0.87f, p.y + r * 0.5f)
                        path.close()
                        drawPath(path, color = c)
                    }
                }
            }
        }
    }
}
