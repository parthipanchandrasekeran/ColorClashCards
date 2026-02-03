package com.parthipan.colorclashcards.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * Debug utilities for tracking Compose recomposition.
 * Only available in debug builds.
 *
 * Usage:
 * 1. Wrap composables with RecompositionTracker to log recompositions
 * 2. Use Modifier.recompositionHighlight() to visually see recompositions
 * 3. Use logRecomposition() to log when a composable recomposes
 */
object RecompositionTracker {

    private const val TAG = "Recomposition"
    private val recompositionCounts = mutableMapOf<String, Int>()

    /**
     * Logs when a composable recomposes and tracks the count.
     *
     * @param name Identifier for the composable
     */
    @Composable
    fun Track(name: String) {
        SideEffect {
            val count = recompositionCounts.getOrDefault(name, 0) + 1
            recompositionCounts[name] = count
            Log.d(TAG, "$name recomposed (count: $count)")
        }
    }

    /**
     * Resets all recomposition counters.
     */
    fun reset() {
        recompositionCounts.clear()
        Log.d(TAG, "Recomposition counters reset")
    }

    /**
     * Gets the current recomposition count for a composable.
     */
    fun getCount(name: String): Int = recompositionCounts.getOrDefault(name, 0)

    /**
     * Logs all current recomposition counts.
     */
    fun logAll() {
        Log.d(TAG, "=== Recomposition Summary ===")
        recompositionCounts.entries
            .sortedByDescending { it.value }
            .forEach { (name, count) ->
                Log.d(TAG, "$name: $count recompositions")
            }
        Log.d(TAG, "=============================")
    }
}

/**
 * Tracks recomposition for a composable with inline logging.
 *
 * Usage:
 * ```
 * @Composable
 * fun MyComposable() {
 *     logRecomposition("MyComposable")
 *     // ... content
 * }
 * ```
 */
@Composable
fun logRecomposition(name: String) {
    val count = remember { mutableIntStateOf(0) }
    SideEffect {
        count.intValue++
        Log.d("Recomposition", "$name recomposed (#${count.intValue})")
    }
}

private class Ref(var value: Int)

/**
 * Modifier that visually highlights recomposition with a random color overlay.
 * More recompositions = more color changes = more visible flashing.
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.recompositionHighlight()) {
 *     // content
 * }
 * ```
 */
fun Modifier.recompositionHighlight(): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        // Draw a semi-transparent colored overlay
        // Color changes on each recomposition
        drawRect(
            color = Color(
                red = Random.nextFloat(),
                green = Random.nextFloat(),
                blue = Random.nextFloat(),
                alpha = 0.1f
            )
        )
    }
)

/**
 * Modifier that shows recomposition count as a colored border.
 * Higher counts = more red border.
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.recompositionBorder(name = "MyBox")) {
 *     // content
 * }
 * ```
 */
@Composable
fun Modifier.recompositionBorder(name: String): Modifier {
    val ref = remember { Ref(0) }
    SideEffect { ref.value++ }

    return this.then(
        Modifier.drawWithContent {
            drawContent()
            // Draw border that gets more red with more recompositions
            val intensity = (ref.value % 20) / 20f
            drawRect(
                color = Color.Red.copy(alpha = intensity * 0.3f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2f + (intensity * 4f)
                )
            )
        }
    )
}

/**
 * Wrapper composable that tracks recomposition of its content.
 *
 * Usage:
 * ```
 * RecompositionBox(name = "PlayerCard") {
 *     // content that might recompose too often
 * }
 * ```
 */
@Composable
fun RecompositionBox(
    name: String,
    showVisual: Boolean = true,
    content: @Composable () -> Unit
) {
    RecompositionTracker.Track(name)

    if (showVisual) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.recompositionHighlight()
        ) {
            content()
        }
    } else {
        content()
    }
}

/**
 * Helper to create a stable lambda that won't cause recomposition.
 *
 * Usage:
 * ```
 * val onClick = rememberStableCallback { viewModel.onClick() }
 * Button(onClick = onClick) { ... }
 * ```
 */
@Composable
inline fun rememberStableCallback(crossinline callback: () -> Unit): () -> Unit {
    return remember { { callback() } }
}

/**
 * Helper to create a stable lambda with one parameter.
 */
@Composable
inline fun <T> rememberStableCallback(crossinline callback: (T) -> Unit): (T) -> Unit {
    return remember { { value: T -> callback(value) } }
}

private fun mutableIntStateOf(initial: Int) = androidx.compose.runtime.mutableIntStateOf(initial)
