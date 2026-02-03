package com.parthipan.colorclashcards.navigation

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parthipan.colorclashcards.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for navigation between modules in Color Clash Cards.
 *
 * Tests verify:
 * - Game Hub shows two game options: Color Clash, Ludo
 * - Clicking Ludo opens Ludo home/lobby screen
 * - Back navigation returns to hub
 * - No crashes on rotate (configuration change)
 * - State restoration works after process death (if enabled)
 *
 * Note: These tests use the actual MainActivity and navigation,
 * but skip authentication by assuming a guest user or mock auth state.
 */
@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ============================================================
    // Test: Game Hub shows two game options
    // ============================================================

    @Test
    fun gameHubDisplaysColorClashOption() {
        // Wait for app to initialize and navigate past splash/auth
        waitForGameHub()

        // Verify Color Clash card is displayed
        composeTestRule.onNodeWithTag("colorClashCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Color Clash").assertIsDisplayed()
    }

    @Test
    fun gameHubDisplaysLudoOption() {
        waitForGameHub()

        // Verify Ludo card is displayed
        composeTestRule.onNodeWithTag("ludoCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ludo").assertIsDisplayed()
    }

    @Test
    fun gameHubDisplaysBothGameCards() {
        waitForGameHub()

        // Verify both game cards are visible
        composeTestRule.onNodeWithTag("colorClashCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ludoCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chooseGameTitle").assertIsDisplayed()
    }

    // ============================================================
    // Test: Clicking Ludo opens Ludo home screen
    // ============================================================

    @Test
    fun clickingLudoCardNavigatesToLudoHome() {
        waitForGameHub()

        // Click on Ludo card
        composeTestRule.onNodeWithTag("ludoCard").performClick()

        // Wait for navigation
        composeTestRule.waitForIdle()

        // Verify Ludo home screen is displayed
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ludoTitle").assertIsDisplayed()
    }

    @Test
    fun ludoHomeShowsPlayOptions() {
        waitForGameHub()

        // Navigate to Ludo
        composeTestRule.onNodeWithTag("ludoCard").performClick()
        composeTestRule.waitForIdle()

        // Verify play buttons are displayed
        composeTestRule.onNodeWithTag("playVsComputerButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playOnlineButton").assertIsDisplayed()
    }

    // ============================================================
    // Test: Clicking Color Clash opens Color Clash home screen
    // ============================================================

    @Test
    fun clickingColorClashCardNavigatesToColorClashHome() {
        waitForGameHub()

        // Click on Color Clash card
        composeTestRule.onNodeWithTag("colorClashCard").performClick()

        // Wait for navigation
        composeTestRule.waitForIdle()

        // Verify Color Clash home screen is displayed
        composeTestRule.onNodeWithTag("colorClashHomeScreen").assertIsDisplayed()
    }

    // ============================================================
    // Test: Back navigation returns to hub
    // ============================================================

    @Test
    fun backFromLudoReturnsToGameHub() {
        waitForGameHub()

        // Navigate to Ludo
        composeTestRule.onNodeWithTag("ludoCard").performClick()
        composeTestRule.waitForIdle()

        // Verify we're on Ludo screen
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()

        // Click back button
        composeTestRule.onNodeWithTag("ludoBackButton").performClick()
        composeTestRule.waitForIdle()

        // Verify we're back at Game Hub
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ludoCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("colorClashCard").assertIsDisplayed()
    }

    @Test
    fun systemBackFromLudoReturnsToGameHub() {
        waitForGameHub()

        // Navigate to Ludo
        composeTestRule.onNodeWithTag("ludoCard").performClick()
        composeTestRule.waitForIdle()

        // Verify we're on Ludo screen
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()

        // Press system back
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Verify we're back at Game Hub
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
    }

    // ============================================================
    // Test: No crashes on rotate (configuration change)
    // ============================================================

    @Test
    fun rotateOnGameHubDoesNotCrash() {
        waitForGameHub()

        // Rotate to landscape
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeTestRule.waitForIdle()

        // Verify content is still displayed
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("colorClashCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ludoCard").assertIsDisplayed()

        // Rotate back to portrait
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeTestRule.waitForIdle()

        // Verify content is still displayed
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
    }

    @Test
    fun rotateOnLudoHomeDoesNotCrash() {
        waitForGameHub()

        // Navigate to Ludo
        composeTestRule.onNodeWithTag("ludoCard").performClick()
        composeTestRule.waitForIdle()

        // Rotate to landscape
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeTestRule.waitForIdle()

        // Verify Ludo screen is still displayed
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playVsComputerButton").assertIsDisplayed()

        // Rotate back to portrait
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeTestRule.waitForIdle()

        // Verify Ludo screen is still displayed
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()
    }

    @Test
    fun rotateWhileNavigatingDoesNotCrash() {
        waitForGameHub()

        // Navigate to Ludo
        composeTestRule.onNodeWithTag("ludoCard").performClick()

        // Rotate immediately
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeTestRule.waitForIdle()

        // Verify Ludo screen is displayed
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()

        // Go back
        composeTestRule.onNodeWithTag("ludoBackButton").performClick()
        composeTestRule.waitForIdle()

        // Rotate back while going back
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeTestRule.waitForIdle()

        // Verify back at Game Hub
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
    }

    // ============================================================
    // Test: State restoration after recreation
    // ============================================================

    @Test
    fun navigationStatePreservedAfterRecreation() {
        waitForGameHub()

        // Navigate to Ludo
        composeTestRule.onNodeWithTag("ludoCard").performClick()
        composeTestRule.waitForIdle()

        // Verify on Ludo screen
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()

        // Recreate activity (simulates process death restoration with saved state)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Verify still on Ludo screen after recreation
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()
    }

    @Test
    fun gameHubStatePreservedAfterRecreation() {
        waitForGameHub()

        // Verify on Game Hub
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()

        // Recreate activity
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Verify still on Game Hub
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("colorClashCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ludoCard").assertIsDisplayed()
    }

    // ============================================================
    // Test: Deep navigation and back stack
    // ============================================================

    @Test
    fun navigateToLudoLobbyAndBack() {
        waitForGameHub()

        // Navigate to Ludo
        composeTestRule.onNodeWithTag("ludoCard").performClick()
        composeTestRule.waitForIdle()

        // Click Play Online to go to lobby
        composeTestRule.onNodeWithTag("playOnlineButton").performClick()
        composeTestRule.waitForIdle()

        // Verify on lobby screen
        composeTestRule.onNodeWithTag("ludoLobbyScreen").assertIsDisplayed()

        // Go back to Ludo home
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Verify back at Ludo home
        composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()

        // Go back to Game Hub
        composeTestRule.onNodeWithTag("ludoBackButton").performClick()
        composeTestRule.waitForIdle()

        // Verify back at Game Hub
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
    }

    @Test
    fun multipleNavigationsWork() {
        waitForGameHub()

        // Navigate to Ludo and back multiple times
        repeat(3) {
            composeTestRule.onNodeWithTag("ludoCard").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("ludoHomeScreen").assertIsDisplayed()

            composeTestRule.onNodeWithTag("ludoBackButton").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
        }

        // Navigate to Color Clash and back
        composeTestRule.onNodeWithTag("colorClashCard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("colorClashHomeScreen").assertIsDisplayed()

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gameHubScreen").assertIsDisplayed()
    }

    // ============================================================
    // Helper Functions
    // ============================================================

    /**
     * Wait for the app to initialize and reach the Game Hub screen.
     * This may involve passing through splash and auth screens.
     */
    private fun waitForGameHub() {
        // Wait for the app to initialize
        composeTestRule.waitForIdle()

        // The app may show splash or auth screens first
        // Try to wait for Game Hub to appear (with timeout)
        try {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeTestRule
                        .onNodeWithTag("gameHubScreen")
                        .fetchSemanticsNode()
                    true
                } catch (e: AssertionError) {
                    // Check if we're on auth screen and need to sign in as guest
                    try {
                        composeTestRule
                            .onNodeWithText("Continue as Guest")
                            .fetchSemanticsNode()
                        // Found guest button, click it
                        composeTestRule.onNodeWithText("Continue as Guest").performClick()
                        false
                    } catch (e2: AssertionError) {
                        // Not on Game Hub yet, but not on auth screen either
                        // Might be on splash, wait more
                        false
                    }
                }
            }
        } catch (e: Exception) {
            // If timeout, try one more click on guest if available
            try {
                composeTestRule.onNodeWithText("Continue as Guest").performClick()
                composeTestRule.waitForIdle()
            } catch (e2: AssertionError) {
                // Ignore - might already be on the right screen
            }
        }

        // Final check
        composeTestRule.waitForIdle()
    }
}
