package com.parthipan.colorclashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.parthipan.colorclashcards.data.preferences.ThemePreferences
import com.parthipan.colorclashcards.ui.navigation.AppNavigation
import com.parthipan.colorclashcards.ui.theme.ColorClashCardsTheme

class MainActivity : ComponentActivity() {
    private lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themePreferences = ThemePreferences(applicationContext)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsState(initial = false)

            ColorClashCardsTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        themePreferences = themePreferences
                    )
                }
            }
        }
    }
}
