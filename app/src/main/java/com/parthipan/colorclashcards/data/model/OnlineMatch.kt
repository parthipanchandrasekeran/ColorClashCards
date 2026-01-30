package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp
import com.parthipan.colorclashcards.game.model.Card
import com.parthipan.colorclashcards.game.model.CardColor
import com.parthipan.colorclashcards.game.model.CardType
import com.parthipan.colorclashcards.game.model.GameState
import com.parthipan.colorclashcards.game.model.PlayDirection
import com.parthipan.colorclashcards.game.model.Player
import com.parthipan.colorclashcards.game.model.TurnPhase

// ==================== PUBLIC MATCH STATE ====================

/**
 * Public match state: rooms/{roomId}/match/public
 * Visible to all players. Host writes, all read.
 */
data class PublicMatchState(
    val currentTurn: String = "",
    val currentColor: String = CardColor.RED.name,
    val direction: Int = 1,
    val turnPhase: String = TurnPhase.PLAY_OR_DRAW.name,
    val topCard: Map<String, Any?> = emptyMap(),
    val handCounts: Map<String, Int> = emptyMap(),
    val drawPileCount: Int = 0,
    val pendingDrawCount: Int = 0,
    val winnerId: String? = null,
    val lastActionId: String? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "currentTurn" to currentTurn,
        "currentColor" to currentColor,
        "direction" to direction,
        "turnPhase" to turnPhase,
        "topCard" to topCard,
        "handCounts" to handCounts,
        "drawPileCount" to drawPileCount,
        "pendingDrawCount" to pendingDrawCount,
        "winnerId" to winnerId,
        "lastActionId" to lastActionId,
        "updatedAt" to Timestamp.now()
    )

    fun getTopCardOrNull(): Card? {
        if (topCard.isEmpty()) return null
        return cardFromMap(topCard)
    }

    companion object {
        fun fromMap(data: Map<String, Any?>): PublicMatchState {
            @Suppress("UNCHECKED_CAST")
            return PublicMatchState(
                currentTurn = data["currentTurn"] as? String ?: "",
                currentColor = data["currentColor"] as? String ?: CardColor.RED.name,
                direction = (data["direction"] as? Long)?.toInt() ?: 1,
                turnPhase = data["turnPhase"] as? String ?: TurnPhase.PLAY_OR_DRAW.name,
                topCard = data["topCard"] as? Map<String, Any?> ?: emptyMap(),
                handCounts = (data["handCounts"] as? Map<String, Long>)?.mapValues { it.value.toInt() } ?: emptyMap(),
                drawPileCount = (data["drawPileCount"] as? Long)?.toInt() ?: 0,
                pendingDrawCount = (data["pendingDrawCount"] as? Long)?.toInt() ?: 0,
                winnerId = data["winnerId"] as? String,
                lastActionId = data["lastActionId"] as? String,
                updatedAt = data["updatedAt"] as? Timestamp
            )
        }

        fun fromGameState(gameState: GameState, lastActionId: String? = null): PublicMatchState {
            val currentPlayer = gameState.players.getOrNull(gameState.currentPlayerIndex)
            val topCard = gameState.discardPile.lastOrNull()?.let { cardToMap(it) } ?: emptyMap()

            return PublicMatchState(
                currentTurn = currentPlayer?.id ?: "",
                currentColor = gameState.currentColor.name,
                direction = if (gameState.direction == PlayDirection.CLOCKWISE) 1 else -1,
                turnPhase = gameState.turnPhase.name,
                topCard = topCard,
                handCounts = gameState.players.associate { it.id to it.hand.size },
                drawPileCount = gameState.deck.size,
                pendingDrawCount = gameState.pendingDrawCount,
                winnerId = gameState.winner?.id,
                lastActionId = lastActionId
            )
        }
    }
}

// ==================== PLAYER HAND ====================

/**
 * Player hand: rooms/{roomId}/match/hands/{playerId}
 * Only readable by that specific player. Host writes all hands.
 */
data class PlayerHand(
    val cards: List<Map<String, Any?>> = emptyList()
) {
    fun toCardList(): List<Card> = cards.map { cardFromMap(it) }

    fun toMap(): Map<String, Any?> = mapOf("cards" to cards)

    companion object {
        fun fromMap(data: Map<String, Any?>): PlayerHand {
            @Suppress("UNCHECKED_CAST")
            return PlayerHand(
                cards = data["cards"] as? List<Map<String, Any?>> ?: emptyList()
            )
        }

        fun fromCardList(cards: List<Card>): PlayerHand {
            return PlayerHand(cards = cards.map { cardToMap(it) })
        }
    }
}

// ==================== PLAYER ACTION ====================

/**
 * Player action: rooms/{roomId}/actions/{actionId}
 * Players create their own actions. Host processes and deletes.
 */
data class PlayerAction(
    val id: String = "",
    val playerId: String = "",
    val type: String = ActionType.PLAY_CARD.name,
    val cardId: String? = null,
    val chosenColor: String? = null,
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "playerId" to playerId,
        "type" to type,
        "cardId" to cardId,
        "chosenColor" to chosenColor,
        "createdAt" to (createdAt ?: Timestamp.now())
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): PlayerAction = PlayerAction(
            id = id,
            playerId = data["playerId"] as? String ?: "",
            type = data["type"] as? String ?: ActionType.PLAY_CARD.name,
            cardId = data["cardId"] as? String,
            chosenColor = data["chosenColor"] as? String,
            createdAt = data["createdAt"] as? Timestamp
        )
    }
}

enum class ActionType { PLAY_CARD, DRAW_CARD, CALL_LAST_CARD }

// ==================== CARD SERIALIZATION ====================

private fun cardToMap(card: Card): Map<String, Any?> = mapOf(
    "id" to card.id,
    "color" to card.color.name,
    "type" to card.type.name,
    "number" to card.number
)

private fun cardFromMap(data: Map<String, Any?>): Card = Card(
    id = data["id"] as? String ?: "",
    color = try { CardColor.valueOf(data["color"] as? String ?: "RED") } catch (e: Exception) { CardColor.RED },
    type = try { CardType.valueOf(data["type"] as? String ?: "NUMBER") } catch (e: Exception) { CardType.NUMBER },
    number = (data["number"] as? Long)?.toInt()
)

// ==================== GAME STATE HELPERS ====================

/**
 * Build a GameState from public state and player data.
 * Used by host to reconstruct full game state.
 */
fun buildGameStateFromPublicAndHands(
    publicState: PublicMatchState,
    playerHands: Map<String, List<Card>>,
    deck: List<Card>,
    discardPile: List<Card>,
    roomPlayers: List<RoomPlayer>
): GameState {
    val playerList = publicState.handCounts.map { (playerId, _) ->
        val roomPlayer = roomPlayers.find { it.odId == playerId }
        val hand = playerHands[playerId] ?: emptyList()
        Player(
            id = playerId,
            name = roomPlayer?.odisplayName ?: "Player",
            isBot = false, // Bots are identified by ID pattern
            hand = hand,
            hasCalledLastCard = false
        )
    }

    return GameState(
        players = playerList,
        currentPlayerIndex = playerList.indexOfFirst { it.id == publicState.currentTurn }.coerceAtLeast(0),
        direction = if (publicState.direction == 1) PlayDirection.CLOCKWISE else PlayDirection.COUNTER_CLOCKWISE,
        turnPhase = try { TurnPhase.valueOf(publicState.turnPhase) } catch (e: Exception) { TurnPhase.PLAY_OR_DRAW },
        currentColor = try { CardColor.valueOf(publicState.currentColor) } catch (e: Exception) { CardColor.RED },
        deck = deck,
        discardPile = discardPile,
        winner = playerList.find { it.id == publicState.winnerId },
        pendingDrawCount = publicState.pendingDrawCount
    )
}
