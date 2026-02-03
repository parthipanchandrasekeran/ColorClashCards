package com.parthipan.colorclashcards.game.ludo.model

/**
 * Represents the state of a token in the Ludo game.
 */
enum class TokenState {
    /**
     * Token is in the home base (starting area).
     * Requires a 6 to move out.
     */
    HOME,

    /**
     * Token is active on the board, moving around the track.
     */
    ACTIVE,

    /**
     * Token has reached the center/finish area.
     * This token is safe and has completed its journey.
     */
    FINISHED
}
