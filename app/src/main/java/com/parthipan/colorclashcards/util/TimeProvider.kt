package com.parthipan.colorclashcards.util

/**
 * Interface for providing current time. Allows for testability by injecting
 * a fake/mock implementation in tests.
 */
interface TimeProvider {
    /**
     * Returns the current time in milliseconds since epoch.
     */
    fun currentTimeMillis(): Long
}

/**
 * Default implementation using system time.
 */
class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
