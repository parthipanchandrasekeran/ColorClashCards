package com.parthipan.colorclashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.parthipan.colorclashcards.audio.LocalSoundManager
import com.parthipan.colorclashcards.audio.SoundManager
import com.parthipan.colorclashcards.data.preferences.AudioPreferences
import com.parthipan.colorclashcards.data.preferences.ThemePreferences
import com.parthipan.colorclashcards.ui.navigation.AppNavigation
import com.parthipan.colorclashcards.ui.theme.ColorClashCardsTheme

class MainActivity : ComponentActivity() {
    private lateinit var themePreferences: ThemePreferences
    private lateinit var audioPreferences: AudioPreferences
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themePreferences = ThemePreferences(applicationContext)
        audioPreferences = AudioPreferences(applicationContext)
        soundManager = SoundManager(applicationContext, audioPreferences)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsState(initial = false)

            ColorClashCardsTheme(darkTheme = isDarkMode) {
                CompositionLocalProvider(LocalSoundManager provides soundManager) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        AppNavigation(
                            navController = navController,
                            themePreferences = themePreferences,
                            audioPreferences = audioPreferences
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
    }

    override fun onResume() {
        super.onResume()
        soundManager.resumeMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}
