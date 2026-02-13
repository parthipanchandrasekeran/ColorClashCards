package com.parthipan.colorclashcards.audio

import com.parthipan.colorclashcards.R

enum class SoundEffect(val resId: Int) {
    DICE_ROLL(R.raw.dice_roll),
    TOKEN_MOVE(R.raw.token_move),
    TOKEN_CAPTURE(R.raw.token_capture),
    TOKEN_FINISH(R.raw.token_finish),
    CARD_PLAY(R.raw.card_play),
    CARD_DRAW(R.raw.card_draw),
    CARD_SKIP(R.raw.card_skip),
    BUTTON_CLICK(R.raw.button_click),
    WIN_FANFARE(R.raw.win_fanfare),
    LOSE_SOUND(R.raw.lose_sound),
    CELEBRATION(R.raw.celebration)
}
