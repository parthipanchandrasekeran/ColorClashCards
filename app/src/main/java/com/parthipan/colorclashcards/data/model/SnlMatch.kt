package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp
import com.parthipan.colorclashcards.game.ludo.model.GameStatus
import com.parthipan.colorclashcards.game.snl.model.*

/**
 * Public SNL match state stored in Firestore.
 * Stored at: snlRooms/{roomId}/match/state
 */
data class SnlMatchState(
    val currentTurnPlayerId: String = "",
    val diceValue: Int? = null,
    val canRollDice: Boolean = true,
    val consecutiveSixes: Int = 0,
    val gameStatus: String = GameStatus.IN_PROGRESS.name,
    val winnerId: String? = null,
    val players: List<SnlMatchPlayer> = emptyList(),
    val finishOrder: List<String> = emptyList(),
    val snakes: Map<String, Int> = emptyMap(),
    val ladders: Map<String, Int> = emptyMap(),
    val boardLayout: String = "CLASSIC",
    val lastMove: SnlMatchMove? = null,
    val turnStartedAt: Timestamp? = null,
    val lastActionAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val AFK_TIMEOUT_MS = 30_000L
        const val REJOIN_TIMEOUT_MS = 60_000L

        fun fromMap(map: Map<String, Any?>): SnlMatchState {
            @Suppress("UNCHECKED_CAST")
            val playersData = map["players"] as? List<Map<String, Any?>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val lastMoveData = map["lastMove"] as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val snakesData = map["snakes"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val laddersData = map["ladders"] as? Map<String, Any?> ?: emptyMap()

            return SnlMatchState(
                currentTurnPlayerId = map["currentTurnPlayerId"] as? String ?: "",
                diceValue = (map["diceValue"] as? Number)?.toInt(),
                canRollDice = map["canRollDice"] as? Boolean ?: true,
                consecutiveSixes = (map["consecutiveSixes"] as? Number)?.toInt() ?: 0,
                gameStatus = map["gameStatus"] as? String ?: GameStatus.IN_PROGRESS.name,
                winnerId = map["winnerId"] as? String,
                players = playersData.map { SnlMatchPlayer.fromMap(it) },
                finishOrder = (map["finishOrder"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                snakes = snakesData.mapValues { (it.value as? Number)?.toInt() ?: 0 },
                ladders = laddersData.mapValues { (it.value as? Number)?.toInt() ?: 0 },
                boardLayout = map["boardLayout"] as? String ?: "CLASSIC",
                lastMove = lastMoveData?.let { SnlMatchMove.fromMap(it) },
                turnStartedAt = map["turnStartedAt"] as? Timestamp,
                lastActionAt = map["lastActionAt"] as? Timestamp,
                updatedAt = map["updatedAt"] as? Timestamp
            )
        }

        fun fromGameState(gameState: SnlGameState): SnlMatchState {
            return SnlMatchState(
                currentTurnPlayerId = gameState.currentTurnPlayerId,
                diceValue = gameState.diceValue,
                canRollDice = gameState.canRollDice,
                consecutiveSixes = gameState.consecutiveSixes,
                gameStatus = gameState.gameStatus.name,
                winnerId = gameState.winnerId,
                players = gameState.players.map { SnlMatchPlayer.fromPlayer(it) },
                finishOrder = gameState.finishOrder,
                snakes = gameState.snakes.mapKeys { it.key.toString() },
                ladders = gameState.ladders.mapKeys { it.key.toString() },
                boardLayout = gameState.boardLayout.name,
                lastMove = gameState.lastMove?.let { SnlMatchMove.fromMove(it) },
                turnStartedAt = Timestamp.now(),
                lastActionAt = Timestamp.now()
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "currentTurnPlayerId" to currentTurnPlayerId,
        "diceValue" to diceValue,
        "canRollDice" to canRollDice,
        "consecutiveSixes" to consecutiveSixes,
        "gameStatus" to gameStatus,
        "winnerId" to winnerId,
        "players" to players.map { it.toMap() },
        "finishOrder" to finishOrder,
        "snakes" to snakes,
        "ladders" to ladders,
        "boardLayout" to boardLayout,
        "lastMove" to lastMove?.toMap(),
        "turnStartedAt" to turnStartedAt,
        "lastActionAt" to lastActionAt,
        "updatedAt" to Timestamp.now()
    )

    fun toGameState(): SnlGameState {
        return SnlGameState(
            players = players.map { it.toPlayer() },
            currentTurnPlayerId = currentTurnPlayerId,
            diceValue = diceValue,
            lastMove = lastMove?.toMove(),
            winnerId = winnerId,
            gameStatus = try { GameStatus.valueOf(gameStatus) } catch (_: Exception) { GameStatus.IN_PROGRESS },
            boardLayout = try { SnlBoardLayout.valueOf(boardLayout) } catch (_: Exception) { SnlBoardLayout.CLASSIC },
            snakes = snakes.mapKeys { it.key.toIntOrNull() ?: 0 }.filterKeys { it > 0 },
            ladders = ladders.mapKeys { it.key.toIntOrNull() ?: 0 }.filterKeys { it > 0 },
            consecutiveSixes = consecutiveSixes,
            canRollDice = canRollDice,
            finishOrder = finishOrder
        )
    }
}

/**
 * Player state within a SNL match.
 */
data class SnlMatchPlayer(
    val id: String = "",
    val name: String = "",
    val colorIndex: Int = 0,
    val position: Int = 0,
    val isBot: Boolean = false,
    val isOnline: Boolean = true,
    val isConnected: Boolean = true
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): SnlMatchPlayer {
            return SnlMatchPlayer(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                colorIndex = (map["colorIndex"] as? Number)?.toInt() ?: 0,
                position = (map["position"] as? Number)?.toInt() ?: 0,
                isBot = map["isBot"] as? Boolean ?: false,
                isOnline = map["isOnline"] as? Boolean ?: true,
                isConnected = map["isConnected"] as? Boolean ?: true
            )
        }

        fun fromPlayer(player: SnlPlayer): SnlMatchPlayer {
            return SnlMatchPlayer(
                id = player.id,
                name = player.name,
                colorIndex = player.colorIndex,
                position = player.position,
                isBot = player.isBot,
                isOnline = player.isOnline,
                isConnected = true
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "colorIndex" to colorIndex,
        "position" to position,
        "isBot" to isBot,
        "isOnline" to isOnline,
        "isConnected" to isConnected
    )

    fun toPlayer(): SnlPlayer {
        return SnlPlayer(
            id = id,
            name = name,
            colorIndex = colorIndex,
            position = position,
            isBot = isBot,
            isOnline = isOnline
        )
    }
}

/**
 * Move record in a SNL match.
 */
data class SnlMatchMove(
    val playerId: String = "",
    val diceValue: Int = 0,
    val fromPos: Int = 0,
    val toPos: Int = 0,
    val finalPos: Int = 0,
    val moveType: String = SnlMoveType.NORMAL.name,
    val timestamp: Long = 0
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): SnlMatchMove {
            return SnlMatchMove(
                playerId = map["playerId"] as? String ?: "",
                diceValue = (map["diceValue"] as? Number)?.toInt() ?: 0,
                fromPos = (map["fromPos"] as? Number)?.toInt() ?: 0,
                toPos = (map["toPos"] as? Number)?.toInt() ?: 0,
                finalPos = (map["finalPos"] as? Number)?.toInt() ?: 0,
                moveType = map["moveType"] as? String ?: SnlMoveType.NORMAL.name,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0
            )
        }

        fun fromMove(move: SnlMove): SnlMatchMove {
            return SnlMatchMove(
                playerId = move.playerId,
                diceValue = move.diceValue,
                fromPos = move.fromPos,
                toPos = move.toPos,
                finalPos = move.finalPos,
                moveType = move.moveType.name,
                timestamp = move.timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "playerId" to playerId,
        "diceValue" to diceValue,
        "fromPos" to fromPos,
        "toPos" to toPos,
        "finalPos" to finalPos,
        "moveType" to moveType,
        "timestamp" to timestamp
    )

    fun toMove(): SnlMove {
        return SnlMove(
            playerId = playerId,
            diceValue = diceValue,
            fromPos = fromPos,
            toPos = toPos,
            finalPos = finalPos,
            moveType = try { SnlMoveType.valueOf(moveType) } catch (_: Exception) { SnlMoveType.NORMAL },
            timestamp = timestamp
        )
    }
}

/**
 * Player action for SNL game.
 * Stored at: snlRooms/{roomId}/actions/{actionId}
 * Only action: ROLL_DICE (no token selection needed).
 */
data class SnlPlayerAction(
    val id: String = "",
    val playerId: String = "",
    val type: String = SnlActionType.ROLL_DICE.name,
    val createdAt: Timestamp? = null
) {
    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): SnlPlayerAction {
            return SnlPlayerAction(
                id = id,
                playerId = map["playerId"] as? String ?: "",
                type = map["type"] as? String ?: SnlActionType.ROLL_DICE.name,
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "playerId" to playerId,
        "type" to type,
        "createdAt" to Timestamp.now()
    )
}

enum class SnlActionType {
    ROLL_DICE,
    HEARTBEAT
}

/**
 * Player presence for SNL game.
 * Stored at: snlRooms/{roomId}/presence/{playerId}
 */
data class SnlPlayerPresence(
    val playerId: String = "",
    val isOnline: Boolean = true,
    val lastSeenAt: Timestamp? = null,
    val disconnectedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): SnlPlayerPresence {
            return SnlPlayerPresence(
                playerId = map["playerId"] as? String ?: "",
                isOnline = map["isOnline"] as? Boolean ?: true,
                lastSeenAt = map["lastSeenAt"] as? Timestamp,
                disconnectedAt = map["disconnectedAt"] as? Timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "playerId" to playerId,
        "isOnline" to isOnline,
        "lastSeenAt" to Timestamp.now(),
        "disconnectedAt" to disconnectedAt
    )
}
