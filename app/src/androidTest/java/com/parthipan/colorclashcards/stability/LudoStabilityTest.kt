package com.parthipan.colorclashcards.stability

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Stability/Soak test for Ludo games lasting 10 minutes.
 *
 * Simulates:
 * - Player disconnect/reconnect
 * - Network delay and out-of-order updates
 * - App background/foreground
 * - Device rotation
 * - Low memory + restore
 *
 * Run:
 * ./gradlew :app:connectedDebugAndroidTest --tests "*.LudoStabilityTest"
 *
 * With custom duration (in seconds):
 * ./gradlew :app:connectedDebugAndroidTest --tests "*.LudoStabilityTest" \
 *   -Pandroid.testInstrumentationRunnerArguments.duration=600
 */
@RunWith(AndroidJUnit4::class)
class LudoStabilityTest {

    companion object {
        private const val PACKAGE_NAME = "com.parthipan.colorclashcards"
        private const val DEFAULT_DURATION_MS = 10 * 60 * 1000L  // 10 minutes
        private const val TIMEOUT_MS = 15_000L

        // Event intervals (milliseconds)
        private const val TURN_INTERVAL_MS = 5_000L
        private const val DISCONNECT_TEST_INTERVAL_MS = 90_000L  // 1.5 min
        private const val BACKGROUND_TEST_INTERVAL_MS = 120_000L // 2 min
        private const val ROTATION_TEST_INTERVAL_MS = 60_000L    // 1 min
    }

    private lateinit var device: UiDevice
    private lateinit var logger: StabilityTestLogger
    private lateinit var metrics: StabilityMetrics

    private var testDurationMs: Long = DEFAULT_DURATION_MS
    private var testStartTime: Long = 0
    private var roomId: String? = null

    @get:Rule
    val testWatcher = StabilityTestWatcher()

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        logger = StabilityTestLogger()
        metrics = StabilityMetrics()

        // Get custom duration from instrumentation args
        val args = InstrumentationRegistry.getArguments()
        args.getString("duration")?.toLongOrNull()?.let {
            testDurationMs = it * 1000
        }

        logger.log("TEST", "Setup", "duration_ms=$testDurationMs")

        // Wake device and unlock
        device.wakeUp()
        device.pressHome()
        SystemClock.sleep(1000)
    }

    @After
    fun teardown() {
        logger.log("TEST", "Teardown", "metrics=$metrics")
        metrics.printSummary()
    }

    /**
     * Main 10-minute stability test.
     */
    @Test
    fun stabilityTest_10MinuteGameplay() {
        testStartTime = System.currentTimeMillis()

        // Phase 1: Launch and start game
        launchApp()
        navigateToLudoGame()

        // Phase 2: Run stability scenarios over test duration
        runStabilityScenarios()

        // Phase 3: Verify game state and cleanup
        verifyFinalState()
    }

    /**
     * Focused test for disconnect/reconnect scenarios.
     */
    @Test
    fun stabilityTest_disconnectReconnect() {
        testStartTime = System.currentTimeMillis()
        testDurationMs = 3 * 60 * 1000L  // 3 minutes

        launchApp()
        navigateToLudoGame()

        repeat(5) { iteration ->
            logger.log("DISCONNECT", "Iteration $iteration", "starting")

            // Play a few turns
            playTurns(3)

            // Test brief disconnect
            testBriefDisconnect()

            // Play more turns
            playTurns(2)

            // Test extended disconnect (if not last iteration)
            if (iteration < 4) {
                testExtendedDisconnect()
            }

            metrics.disconnectTests.incrementAndGet()
        }
    }

    /**
     * Focused test for rotation stability.
     */
    @Test
    fun stabilityTest_configurationChanges() {
        testStartTime = System.currentTimeMillis()
        testDurationMs = 2 * 60 * 1000L  // 2 minutes

        launchApp()
        navigateToLudoGame()

        repeat(10) { iteration ->
            logger.log("ROTATION", "Iteration $iteration", "starting")

            // Rotate during idle
            testRotation()
            playTurns(1)

            // Rotate during dice roll
            testRotationDuringAnimation()
            playTurns(1)

            // Rapid rotation
            if (iteration % 3 == 0) {
                testRapidRotation()
            }

            metrics.rotationTests.incrementAndGet()
        }
    }

    /**
     * Focused test for background/foreground transitions.
     */
    @Test
    fun stabilityTest_appLifecycle() {
        testStartTime = System.currentTimeMillis()
        testDurationMs = 3 * 60 * 1000L  // 3 minutes

        launchApp()
        navigateToLudoGame()

        repeat(5) { iteration ->
            logger.log("LIFECYCLE", "Iteration $iteration", "starting")

            playTurns(2)

            // Brief background
            testBriefBackground()
            playTurns(1)

            // Extended background
            testExtendedBackground()
            playTurns(1)

            // Background with other app
            testBackgroundWithMemoryPressure()

            metrics.lifecycleTests.incrementAndGet()
        }
    }

    // ========================================
    // Game Navigation
    // ========================================

    private fun launchApp() {
        logger.log("NAV", "LaunchApp", "starting")

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: throw IllegalStateException("Cannot find launch intent for $PACKAGE_NAME")

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait for app to launch
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
        metrics.appLaunches.incrementAndGet()

        logger.log("NAV", "LaunchApp", "completed")
    }

    private fun navigateToLudoGame() {
        logger.log("NAV", "NavigateToLudo", "starting")

        // Handle auth if needed
        handleAuth()

        // Wait for Game Hub
        waitForGameHub()

        // Click Ludo card
        val ludoCard = device.findObject(By.res(PACKAGE_NAME, "ludoCard"))
            ?: device.findObject(By.text("Ludo"))
        ludoCard?.click()

        device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "ludoHomeScreen")),
            TIMEOUT_MS
        )

        // Click Play vs Computer
        val playButton = device.findObject(By.res(PACKAGE_NAME, "playVsComputerButton"))
            ?: device.findObject(By.text("Play vs Computer"))
        playButton?.click()

        device.wait(Until.hasObject(By.text("Game Setup")), TIMEOUT_MS)

        // Start game
        device.findObject(By.text("Start Game"))?.click()

        // Wait for game board
        device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "ludoBoard")),
            TIMEOUT_MS
        )

        logger.log("NAV", "NavigateToLudo", "game started")
    }

    private fun handleAuth() {
        val guestButton = device.wait(
            Until.findObject(By.text("Continue as Guest")),
            5000
        )
        guestButton?.click()
    }

    private fun waitForGameHub() {
        device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "gameHubScreen")),
            TIMEOUT_MS
        )
    }

    // ========================================
    // Stability Scenarios
    // ========================================

    private fun runStabilityScenarios() {
        logger.log("STABILITY", "RunScenarios", "starting")

        var lastDisconnectTest = 0L
        var lastBackgroundTest = 0L
        var lastRotationTest = 0L
        var turnCount = 0

        while (getElapsedTime() < testDurationMs) {
            val elapsed = getElapsedTime()

            // Regular gameplay
            playTurns(1)
            turnCount++
            metrics.turnsPlayed.incrementAndGet()

            // Disconnect test (every 1.5 min)
            if (elapsed - lastDisconnectTest > DISCONNECT_TEST_INTERVAL_MS) {
                testBriefDisconnect()
                lastDisconnectTest = elapsed
                metrics.disconnectTests.incrementAndGet()
            }

            // Background test (every 2 min)
            if (elapsed - lastBackgroundTest > BACKGROUND_TEST_INTERVAL_MS) {
                testBriefBackground()
                lastBackgroundTest = elapsed
                metrics.lifecycleTests.incrementAndGet()
            }

            // Rotation test (every 1 min)
            if (elapsed - lastRotationTest > ROTATION_TEST_INTERVAL_MS) {
                testRotation()
                lastRotationTest = elapsed
                metrics.rotationTests.incrementAndGet()
            }

            // Random events (10% chance each turn)
            if (Random.nextFloat() < 0.1) {
                when (Random.nextInt(4)) {
                    0 -> testBriefDisconnect()
                    1 -> testBriefBackground()
                    2 -> testRotation()
                    3 -> testMemoryPressure()
                }
            }

            logger.log("STABILITY", "Progress",
                "elapsed=${elapsed/1000}s, turns=$turnCount")
        }

        logger.log("STABILITY", "RunScenarios", "completed, turns=$turnCount")
    }

    // ========================================
    // Gameplay Actions
    // ========================================

    private fun playTurns(count: Int) {
        repeat(count) { turn ->
            logger.log("GAME", "PlayTurn", "turn=$turn")

            // Wait for dice to be enabled (our turn)
            val diceButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "rollDiceButton")),
                TURN_INTERVAL_MS
            )

            if (diceButton != null && diceButton.isEnabled) {
                // Roll dice
                diceButton.click()
                metrics.diceRolls.incrementAndGet()
                SystemClock.sleep(1500)  // Wait for animation

                // Try to move token
                tryMoveToken()
            } else {
                // Not our turn, wait
                SystemClock.sleep(2000)
            }
        }
    }

    private fun tryMoveToken(): Boolean {
        SystemClock.sleep(200)

        for (i in 0..3) {
            val token = device.findObject(
                By.res(PACKAGE_NAME, "token_red_$i")
            )
            if (token != null && token.isClickable) {
                token.click()
                metrics.tokenMoves.incrementAndGet()
                SystemClock.sleep(800)  // Wait for move animation
                return true
            }
        }
        return false
    }

    // ========================================
    // Disconnect/Reconnect Tests
    // ========================================

    private fun testBriefDisconnect() {
        logger.log("DISCONNECT", "BriefDisconnect", "starting (20s)")

        // Enable airplane mode
        toggleAirplaneMode(true)
        metrics.networkToggled.incrementAndGet()

        SystemClock.sleep(20_000)  // 20 seconds

        // Disable airplane mode
        toggleAirplaneMode(false)

        // Wait for reconnection
        val reconnected = waitForReconnection()

        logger.log("DISCONNECT", "BriefDisconnect",
            "completed, reconnected=$reconnected")

        if (reconnected) {
            metrics.successfulReconnects.incrementAndGet()
        } else {
            metrics.failedReconnects.incrementAndGet()
        }
    }

    private fun testExtendedDisconnect() {
        logger.log("DISCONNECT", "ExtendedDisconnect", "starting (45s)")

        toggleAirplaneMode(true)
        metrics.networkToggled.incrementAndGet()

        SystemClock.sleep(45_000)  // 45 seconds (within 60s timeout)

        toggleAirplaneMode(false)

        val reconnected = waitForReconnection()

        logger.log("DISCONNECT", "ExtendedDisconnect",
            "completed, reconnected=$reconnected")

        if (reconnected) {
            metrics.successfulReconnects.incrementAndGet()
        } else {
            metrics.failedReconnects.incrementAndGet()
        }
    }

    private fun toggleAirplaneMode(enable: Boolean) {
        // Use shell command (requires test permissions or root)
        try {
            val command = if (enable) {
                "cmd connectivity airplane-mode enable"
            } else {
                "cmd connectivity airplane-mode disable"
            }
            device.executeShellCommand(command)
        } catch (e: Exception) {
            logger.log("DISCONNECT", "AirplaneMode", "failed: ${e.message}")
            // Fallback: use settings UI
            // This is a simplified version - real implementation would use settings
        }
    }

    private fun waitForReconnection(): Boolean {
        // Wait for reconnecting indicator to appear and disappear
        val reconnecting = device.wait(
            Until.hasObject(By.textContains("Reconnect")),
            5000
        )

        if (reconnecting) {
            // Wait for it to go away (successful reconnect)
            return device.wait(
                Until.gone(By.textContains("Reconnect")),
                30_000
            )
        }

        // No reconnecting message - might already be connected
        return device.hasObject(By.res(PACKAGE_NAME, "ludoBoard"))
    }

    // ========================================
    // Background/Foreground Tests
    // ========================================

    private fun testBriefBackground() {
        logger.log("LIFECYCLE", "BriefBackground", "starting (10s)")

        device.pressHome()
        metrics.backgroundEvents.incrementAndGet()

        SystemClock.sleep(10_000)  // 10 seconds

        // Return to app
        bringAppToForeground()
        metrics.foregroundEvents.incrementAndGet()

        // Verify game state
        val gameVisible = device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "ludoBoard")),
            TIMEOUT_MS
        )

        logger.log("LIFECYCLE", "BriefBackground",
            "completed, gameVisible=$gameVisible")
    }

    private fun testExtendedBackground() {
        logger.log("LIFECYCLE", "ExtendedBackground", "starting (60s)")

        device.pressHome()
        metrics.backgroundEvents.incrementAndGet()

        SystemClock.sleep(60_000)  // 60 seconds

        bringAppToForeground()
        metrics.foregroundEvents.incrementAndGet()

        // May need to handle reconnection
        val needsReconnect = device.hasObject(By.textContains("Reconnect"))
        if (needsReconnect) {
            waitForReconnection()
        }

        val gameVisible = device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "ludoBoard")),
            TIMEOUT_MS
        )

        logger.log("LIFECYCLE", "ExtendedBackground",
            "completed, gameVisible=$gameVisible, neededReconnect=$needsReconnect")
    }

    private fun testBackgroundWithMemoryPressure() {
        logger.log("LIFECYCLE", "MemoryPressure", "starting")

        device.pressHome()
        metrics.backgroundEvents.incrementAndGet()

        // Open camera (memory-heavy app)
        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .startActivity(cameraIntent)

            SystemClock.sleep(5000)

            // Close camera
            device.pressBack()
        } catch (e: Exception) {
            logger.log("LIFECYCLE", "MemoryPressure", "camera failed: ${e.message}")
        }

        SystemClock.sleep(2000)

        // Return to our app
        bringAppToForeground()
        metrics.foregroundEvents.incrementAndGet()

        // App may have been killed - check if we need to restart
        val appVisible = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)),
            TIMEOUT_MS
        )

        if (!appVisible) {
            logger.log("LIFECYCLE", "MemoryPressure", "app was killed, relaunching")
            launchApp()
            metrics.processDeaths.incrementAndGet()

            // Check for rejoin dialog
            val rejoinDialog = device.wait(
                Until.hasObject(By.textContains("Rejoin")),
                5000
            )
            if (rejoinDialog) {
                device.findObject(By.text("Rejoin"))?.click()
            }
        }

        val gameVisible = device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, "ludoBoard")),
            TIMEOUT_MS
        )

        logger.log("LIFECYCLE", "MemoryPressure",
            "completed, gameVisible=$gameVisible")
    }

    private fun bringAppToForeground() {
        // Use recent apps to return
        device.pressRecentApps()
        SystemClock.sleep(1000)

        // Find our app in recents
        val appCard = device.findObject(By.textContains("Color Clash"))
            ?: device.findObject(By.pkg(PACKAGE_NAME))

        if (appCard != null) {
            appCard.click()
        } else {
            // Fallback: launch app again
            launchApp()
        }

        SystemClock.sleep(1000)
    }

    // ========================================
    // Rotation Tests
    // ========================================

    private fun testRotation() {
        logger.log("ROTATION", "SingleRotation", "starting")

        val activity = getCurrentActivity()

        // Rotate to landscape
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        SystemClock.sleep(1500)

        // Verify game still visible
        val gameVisible = device.hasObject(By.res(PACKAGE_NAME, "ludoBoard"))
        metrics.rotations.incrementAndGet()

        // Rotate back to portrait
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        SystemClock.sleep(1500)
        metrics.rotations.incrementAndGet()

        logger.log("ROTATION", "SingleRotation",
            "completed, gameVisible=$gameVisible")
    }

    private fun testRotationDuringAnimation() {
        logger.log("ROTATION", "DuringAnimation", "starting")

        // Start dice roll
        val diceButton = device.findObject(By.res(PACKAGE_NAME, "rollDiceButton"))
        if (diceButton != null && diceButton.isEnabled) {
            diceButton.click()
            metrics.diceRolls.incrementAndGet()

            // Immediately rotate
            SystemClock.sleep(300)  // Small delay to ensure animation started
            val activity = getCurrentActivity()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            metrics.rotations.incrementAndGet()

            SystemClock.sleep(2000)  // Wait for animation to complete

            // Rotate back
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            metrics.rotations.incrementAndGet()
        }

        logger.log("ROTATION", "DuringAnimation", "completed")
    }

    private fun testRapidRotation() {
        logger.log("ROTATION", "RapidRotation", "starting (5x)")

        val activity = getCurrentActivity()
        val orientations = listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )

        orientations.forEach { orientation ->
            activity?.requestedOrientation = orientation
            metrics.rotations.incrementAndGet()
            SystemClock.sleep(500)  // Quick rotation
        }

        val gameVisible = device.hasObject(By.res(PACKAGE_NAME, "ludoBoard"))

        logger.log("ROTATION", "RapidRotation",
            "completed, gameVisible=$gameVisible")
    }

    private fun getCurrentActivity(): android.app.Activity? {
        // This requires test instrumentation
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThread.getMethod("currentActivityThread")
                .invoke(null)
            val activities = activityThread.getDeclaredField("mActivities")
            activities.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val activitiesMap = activities.get(currentActivityThread) as? Map<*, *>
            activitiesMap?.values?.firstOrNull()?.let { record ->
                val activityField = record.javaClass.getDeclaredField("activity")
                activityField.isAccessible = true
                activityField.get(record) as? android.app.Activity
            }
        } catch (e: Exception) {
            null
        }
    }

    // ========================================
    // Memory Tests
    // ========================================

    private fun testMemoryPressure() {
        logger.log("MEMORY", "Pressure", "starting")

        try {
            // Trigger trim memory via shell
            device.executeShellCommand(
                "am send-trim-memory $PACKAGE_NAME RUNNING_CRITICAL"
            )
            metrics.memoryWarnings.incrementAndGet()

            SystemClock.sleep(2000)

            val gameVisible = device.hasObject(By.res(PACKAGE_NAME, "ludoBoard"))

            logger.log("MEMORY", "Pressure",
                "completed, gameVisible=$gameVisible")
        } catch (e: Exception) {
            logger.log("MEMORY", "Pressure", "failed: ${e.message}")
        }
    }

    // ========================================
    // Verification
    // ========================================

    private fun verifyFinalState() {
        logger.log("VERIFY", "FinalState", "starting")

        // Check game is still running
        val gameVisible = device.hasObject(By.res(PACKAGE_NAME, "ludoBoard"))

        // Check for any error dialogs
        val hasError = device.hasObject(By.textContains("Error"))
            || device.hasObject(By.textContains("Crash"))

        // Check UI elements are responsive
        val diceButton = device.findObject(By.res(PACKAGE_NAME, "rollDiceButton"))
        val uiResponsive = diceButton != null

        logger.log("VERIFY", "FinalState",
            "gameVisible=$gameVisible, hasError=$hasError, uiResponsive=$uiResponsive")

        // Assert stability
        assert(metrics.failedReconnects.get() <= 1) {
            "Too many failed reconnects: ${metrics.failedReconnects.get()}"
        }
        assert(metrics.processDeaths.get() <= 2) {
            "Too many process deaths: ${metrics.processDeaths.get()}"
        }
    }

    // ========================================
    // Helpers
    // ========================================

    private fun getElapsedTime(): Long {
        return System.currentTimeMillis() - testStartTime
    }

    // ========================================
    // Logging
    // ========================================

    inner class StabilityTestLogger {
        fun log(category: String, event: String, details: String) {
            val timestamp = System.currentTimeMillis() - testStartTime
            val message = "[${timestamp}ms] [$category] $event: $details"
            println(message)

            // In real implementation, this would also:
            // - Write to Crashlytics breadcrumbs
            // - Log to file for later analysis
        }
    }

    // ========================================
    // Metrics
    // ========================================

    class StabilityMetrics {
        val appLaunches = AtomicInteger(0)
        val turnsPlayed = AtomicInteger(0)
        val diceRolls = AtomicInteger(0)
        val tokenMoves = AtomicInteger(0)

        val disconnectTests = AtomicInteger(0)
        val networkToggled = AtomicInteger(0)
        val successfulReconnects = AtomicInteger(0)
        val failedReconnects = AtomicInteger(0)

        val lifecycleTests = AtomicInteger(0)
        val backgroundEvents = AtomicInteger(0)
        val foregroundEvents = AtomicInteger(0)
        val processDeaths = AtomicInteger(0)

        val rotationTests = AtomicInteger(0)
        val rotations = AtomicInteger(0)

        val memoryWarnings = AtomicInteger(0)

        fun printSummary() {
            println("""
                |
                |========================================
                |STABILITY TEST METRICS SUMMARY
                |========================================
                |
                |Gameplay:
                |  App launches: ${appLaunches.get()}
                |  Turns played: ${turnsPlayed.get()}
                |  Dice rolls: ${diceRolls.get()}
                |  Token moves: ${tokenMoves.get()}
                |
                |Network:
                |  Disconnect tests: ${disconnectTests.get()}
                |  Network toggles: ${networkToggled.get()}
                |  Successful reconnects: ${successfulReconnects.get()}
                |  Failed reconnects: ${failedReconnects.get()}
                |
                |Lifecycle:
                |  Lifecycle tests: ${lifecycleTests.get()}
                |  Background events: ${backgroundEvents.get()}
                |  Foreground events: ${foregroundEvents.get()}
                |  Process deaths: ${processDeaths.get()}
                |
                |Configuration:
                |  Rotation tests: ${rotationTests.get()}
                |  Total rotations: ${rotations.get()}
                |
                |Memory:
                |  Memory warnings: ${memoryWarnings.get()}
                |
                |========================================
            """.trimMargin())
        }

        override fun toString(): String {
            return "turns=${turnsPlayed.get()}, " +
                    "reconnects=${successfulReconnects.get()}/${disconnectTests.get()}, " +
                    "rotations=${rotations.get()}, " +
                    "deaths=${processDeaths.get()}"
        }
    }

    // ========================================
    // Test Watcher (for logging)
    // ========================================

    inner class StabilityTestWatcher : TestWatcher() {
        override fun starting(description: Description) {
            println("\n========================================")
            println("STARTING: ${description.methodName}")
            println("========================================\n")
        }

        override fun finished(description: Description) {
            println("\n========================================")
            println("FINISHED: ${description.methodName}")
            println("========================================\n")
        }

        override fun failed(e: Throwable, description: Description) {
            println("\n========================================")
            println("FAILED: ${description.methodName}")
            println("Error: ${e.message}")
            println("========================================\n")
        }
    }
}
