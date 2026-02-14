package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp
import com.parthipan.colorclashcards.game.ludo.model.*

/**
 * Public Ludo match state stored in Firestore.
 * Stored at: ludoRooms/{roomId}/match/state
 *
 * Contains all game state visible to all players.
 */
data class LudoMatchState(
    val currentTurnPlayerId: String = "",
    val diceValue: Int? = null,
    val canRollDice: Boolean = true,
    val mustSelectToken: Boolean = false,
    val consecutiveSixes: Int = 0,
    val gameStatus: String = GameStatus.IN_PROGRESS.name,
    val winnerId: String? = null,
    val players: List<LudoMatchPlayer> = emptyList(),
    val finishOrder: List<String> = emptyList(),
    val lastMove: LudoMatchMove? = null,
    val turnStartedAt: Timestamp? = null,      // For AFK timeout
    val lastActionAt: Timestamp? = null,       // Track activity
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val AFK_TIMEOUT_MS = 30_000L      // 30 seconds
        const val REJOIN_TIMEOUT_MS = 60_000L  // 60 seconds

        fun fromMap(map: Map<String, Any?>): LudoMatchState {
            @Suppress("UNCHECKED_CAST")
            val playersData = map["players"] as? List<Map<String, Any?>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val lastMoveData = map["lastMove"] as? Map<String, Any?>

            return LudoMatchState(
                currentTurnPlayerId = map["currentTurnPlayerId"] as? String ?: "",
                diceValue = (map["diceValue"] as? Number)?.toInt(),
                canRollDice = map["canRollDice"] as? Boolean ?: true,
                mustSelectToken = map["mustSelectToken"] as? Boolean ?: false,
                consecutiveSixes = (map["consecutiveSixes"] as? Number)?.toInt() ?: 0,
                gameStatus = map["gameStatus"] as? String ?: GameStatus.IN_PROGRESS.name,
                winnerId = map["winnerId"] as? String,
                players = playersData.map { LudoMatchPlayer.fromMap(it) },
                finishOrder = (map["finishOrder"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                lastMove = lastMoveData?.let { LudoMatchMove.fromMap(it) },
                turnStartedAt = map["turnStartedAt"] as? Timestamp,
                lastActionAt = map["lastActionAt"] as? Timestamp,
                updatedAt = map["updatedAt"] as? Timestamp
            )
        }

        fun fromGameState(gameState: LudoGameState): LudoMatchState {
            return LudoMatchState(
                currentTurnPlayerId = gameState.currentTurnPlayerId,
                diceValue = gameState.diceValue,
                canRollDice = gameState.canRollDice,
                mustSelectToken = gameState.mustSelectToken,
                consecutiveSixes = gameState.consecutiveSixes,
                gameStatus = gameState.gameStatus.name,
                winnerId = gameState.winnerId,
                players = gameState.players.map { LudoMatchPlayer.fromPlayer(it) },
                finishOrder = gameState.finishOrder,
                lastMove = gameState.lastMove?.let { LudoMatchMove.fromMove(it) },
                turnStartedAt = Timestamp.now(),
                lastActionAt = Timestamp.now()
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "currentTurnPlayerId" to currentTurnPlayerId,
        "diceValue" to diceValue,
        "canRollDice" to canRollDice,
        "mustSelectToken" to mustSelectToken,
        "consecutiveSixes" to consecutiveSixes,
        "gameStatus" to gameStatus,
        "winnerId" to winnerId,
        "players" to players.map { it.toMap() },
        "finishOrder" to finishOrder,
        "lastMove" to lastMove?.toMap(),
        "turnStartedAt" to turnStartedAt,
        "lastActionAt" to lastActionAt,
        "updatedAt" to Timestamp.now()
    )

    fun toGameState(): LudoGameState {
        return LudoGameState(
            players = players.map { it.toPlayer() },
            currentTurnPlayerId = currentTurnPlayerId,
            diceValue = diceValue,
            lastMove = lastMove?.toMove(),
            winnerId = winnerId,
            gameStatus = try { GameStatus.valueOf(gameStatus) } catch (e: Exception) { GameStatus.IN_PROGRESS },
            consecutiveSixes = consecutiveSixes,
            canRollDice = canRollDice,
            mustSelectToken = mustSelectToken,
            finishOrder = finishOrder
        )
    }
}

/**
 * Player state within a Ludo match.
 */
data class LudoMatchPlayer(
    val id: String = "",
    val name: String = "",
    val color: String = "",
    val isBot: Boolean = false,
    val isOnline: Boolean = true,
    val isConnected: Boolean = true,
    val tokens: List<LudoMatchToken> = emptyList()
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): LudoMatchPlayer {
            @Suppress("UNCHECKED_CAST")
            val tokensData = map["tokens"] as? List<Map<String, Any?>> ?: emptyList()
            return LudoMatchPlayer(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                color = map["color"] as? String ?: "",
                isBot = map["isBot"] as? Boolean ?: false,
                isOnline = map["isOnline"] as? Boolean ?: true,
                isConnected = map["isConnected"] as? Boolean ?: true,
                tokens = tokensData.map { LudoMatchToken.fromMap(it) }
            )
        }

        fun fromPlayer(player: LudoPlayer): LudoMatchPlayer {
            return LudoMatchPlayer(
                id = player.id,
                name = player.name,
                color = player.color.name,
                isBot = player.isBot,
                isOnline = player.isOnline,
                isConnected = true,
                tokens = player.tokens.map { LudoMatchToken.fromToken(it) }
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "color" to color,
        "isBot" to isBot,
        "isOnline" to isOnline,
        "isConnected" to isConnected,
        "tokens" to tokens.map { it.toMap() }
    )

    fun toPlayer(): LudoPlayer {
        // Color is keyed by LudoColor enum name - must be valid
        val playerColor = try {
            LudoColor.valueOf(color)
        } catch (e: Exception) {
            // This should never happen if data is stored correctly
            // Throw exception to make bug visible rather than silently using wrong color
            throw IllegalStateException("Invalid player color '$color' for player '$name' (id: $id). " +
                "This indicates corrupted match data.")
        }
        return LudoPlayer(
            id = id,
            name = name,
            color = playerColor,
            tokens = tokens.map { it.toToken() },
            isBot = isBot,
            isOnline = isOnline
        )
    }
}

/**
 * Token state within a match.
 */
data class LudoMatchToken(
    val id: Int = 0,
    val state: String = TokenState.HOME.name,
    val position: Int = -1
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): LudoMatchToken {
            return LudoMatchToken(
                id = (map["id"] as? Number)?.toInt() ?: 0,
                state = map["state"] as? String ?: TokenState.HOME.name,
                position = (map["position"] as? Number)?.toInt() ?: -1
            )
        }

        fun fromToken(token: Token): LudoMatchToken {
            return LudoMatchToken(
                id = token.id,
                state = token.state.name,
                position = token.position
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "state" to state,
        "position" to position
    )

    fun toToken(): Token {
        return Token(
            id = id,
            state = try { TokenState.valueOf(state) } catch (e: Exception) { TokenState.HOME },
            position = position
        )
    }
}

/**
 * Move record in a Ludo match.
 */
data class LudoMatchMove(
    val playerId: String = "",
    val tokenId: Int = 0,
    val diceValue: Int = 0,
    val fromPosition: Int = 0,
    val toPosition: Int = 0,
    val moveType: String = MoveType.NORMAL.name,
    val capturedPlayerId: String? = null,
    val capturedTokenId: Int? = null,
    val timestamp: Long = 0
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): LudoMatchMove {
            return LudoMatchMove(
                playerId = map["playerId"] as? String ?: "",
                tokenId = (map["tokenId"] as? Number)?.toInt() ?: 0,
                diceValue = (map["diceValue"] as? Number)?.toInt() ?: 0,
                fromPosition = (map["fromPosition"] as? Number)?.toInt() ?: 0,
                toPosition = (map["toPosition"] as? Number)?.toInt() ?: 0,
                moveType = map["moveType"] as? String ?: MoveType.NORMAL.name,
                capturedPlayerId = map["capturedPlayerId"] as? String,
                capturedTokenId = (map["capturedTokenId"] as? Number)?.toInt(),
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0
            )
        }

        fun fromMove(move: LudoMove): LudoMatchMove {
            return LudoMatchMove(
                playerId = move.playerId,
                tokenId = move.tokenId,
                diceValue = move.diceValue,
                fromPosition = move.fromPosition,
                toPosition = move.toPosition,
                moveType = move.moveType.name,
                capturedPlayerId = move.capturedTokenInfo?.playerId,
                capturedTokenId = move.capturedTokenInfo?.tokenId,
                timestamp = move.timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "playerId" to playerId,
        "tokenId" to tokenId,
        "diceValue" to diceValue,
        "fromPosition" to fromPosition,
        "toPosition" to toPosition,
        "moveType" to moveType,
        "capturedPlayerId" to capturedPlayerId,
        "capturedTokenId" to capturedTokenId,
        "timestamp" to timestamp
    )

    fun toMove(): LudoMove {
        val capturedInfo = if (capturedPlayerId != null && capturedTokenId != null) {
            CapturedTokenInfo(
                playerId = capturedPlayerId,
                tokenId = capturedTokenId,
                position = 0 // Position not stored, not critical for replay
            )
        } else null

        return LudoMove(
            playerId = playerId,
            tokenId = tokenId,
            diceValue = diceValue,
            fromPosition = fromPosition,
            toPosition = toPosition,
            moveType = try { MoveType.valueOf(moveType) } catch (e: Exception) { MoveType.NORMAL },
            capturedTokenInfo = capturedInfo,
            timestamp = timestamp
        )
    }
}

/**
 * Player action for Ludo game.
 * Stored at: ludoRooms/{roomId}/actions/{actionId}
 */
data class LudoPlayerAction(
    val id: String = "",
    val playerId: String = "",
    val type: String = LudoActionType.ROLL_DICE.name,
    val tokenId: Int? = null,
    val createdAt: Timestamp? = null
) {
    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): LudoPlayerAction {
            return LudoPlayerAction(
                id = id,
                playerId = map["playerId"] as? String ?: "",
                type = map["type"] as? String ?: LudoActionType.ROLL_DICE.name,
                tokenId = (map["tokenId"] as? Number)?.toInt(),
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "playerId" to playerId,
        "type" to type,
        "tokenId" to tokenId,
        "createdAt" to Timestamp.now()
    )
}

/**
 * Types of player actions in Ludo.
 */
enum class LudoActionType {
    ROLL_DICE,
    MOVE_TOKEN,
    HEARTBEAT    // Keep-alive to track connection
}

/**
 * Player presence tracking for disconnect handling.
 * Stored at: ludoRooms/{roomId}/presence/{playerId}
 */
data class LudoPlayerPresence(
    val playerId: String = "",
    val isOnline: Boolean = true,
    val lastSeenAt: Timestamp? = null,
    val disconnectedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): LudoPlayerPresence {
            return LudoPlayerPresence(
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
