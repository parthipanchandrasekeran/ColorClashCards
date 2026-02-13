package com.parthipan.colorclashcards.audio

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSoundManager = staticCompositionLocalOf<SoundManager> {
    error("No SoundManager provided")
}
