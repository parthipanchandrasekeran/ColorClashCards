package com.parthipan.colorclashcards.game.snl.engine

import com.parthipan.colorclashcards.game.ludo.model.GameStatus
import com.parthipan.colorclashcards.game.snl.model.SnlGameState
import com.parthipan.colorclashcards.game.snl.model.SnlMoveType
import com.parthipan.colorclashcards.game.snl.model.SnlPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnlEngineTest {

    @Test
    fun `player off board must roll one to start`() {
        val p1 = SnlPlayer(id = "p1", name = "P1", colorIndex = 0, position = 0)
        val p2 = SnlPlayer(id = "p2", name = "P2", colorIndex = 1, position = 0)
        val state = SnlGameState(
            players = listOf(p1, p2),
            currentTurnPlayerId = p1.id,
            gameStatus = GameStatus.IN_PROGRESS,
            snakes = emptyMap(),
            ladders = emptyMap()
        )

        val result = SnlEngine.rollDice(state, 3)

        assertEquals(SnlMoveType.NEED_ONE_TO_START, result.move?.moveType)
        assertEquals(0, result.newState.getPlayer(p1.id)?.position)
        assertEquals(p2.id, result.newState.currentTurnPlayerId)
        assertFalse(result.bonusTurn)
    }

    @Test
    fun `rolling six before start gives bonus turn but does not enter board`() {
        val p1 = SnlPlayer(id = "p1", name = "P1", colorIndex = 0, position = 0)
        val p2 = SnlPlayer(id = "p2", name = "P2", colorIndex = 1, position = 0)
        val state = SnlGameState(
            players = listOf(p1, p2),
            currentTurnPlayerId = p1.id,
            gameStatus = GameStatus.IN_PROGRESS,
            snakes = emptyMap(),
            ladders = emptyMap()
        )

        val result = SnlEngine.rollDice(state, 6)

        assertEquals(SnlMoveType.NEED_ONE_TO_START, result.move?.moveType)
        assertEquals(0, result.newState.getPlayer(p1.id)?.position)
        assertEquals(p1.id, result.newState.currentTurnPlayerId)
        assertTrue(result.bonusTurn)
    }

    @Test
    fun `ladder to 100 finishes game immediately`() {
        val p1 = SnlPlayer(id = "p1", name = "P1", colorIndex = 0, position = 98)
        val p2 = SnlPlayer(id = "p2", name = "P2", colorIndex = 1, position = 20)
        val state = SnlGameState(
            players = listOf(p1, p2),
            currentTurnPlayerId = p1.id,
            gameStatus = GameStatus.IN_PROGRESS,
            snakes = emptyMap(),
            ladders = mapOf(99 to 100)
        )

        val result = SnlEngine.rollDice(state, 1)

        assertTrue(result.playerFinished)
        assertTrue(result.newState.isGameOver)
        assertEquals(p1.id, result.newState.winnerId)
        assertEquals(GameStatus.FINISHED, result.newState.gameStatus)
        assertEquals(listOf(p1.id, p2.id), result.newState.finishOrder)
        assertEquals(SnlMoveType.WIN, result.move?.moveType)
    }
}
