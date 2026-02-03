package com.parthipan.colorclashcards.game.ludo

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.parthipan.colorclashcards.game.ludo.model.*
import kotlinx.coroutines.tasks.await

/**
 * Repository for Ludo game operations.
 * Handles communication with Firebase Cloud Functions for server-safe game logic.
 */
class LudoRepository {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    /**
     * Roll the dice for the current turn.
     * Server generates the dice value to prevent cheating.
     *
     * @param roomId The game room ID
     * @return Result containing dice roll outcome
     */
    suspend fun rollDice(roomId: String): Result<DiceRollResult> {
        return try {
            val data = hashMapOf("roomId" to roomId)

            val result = functions
                .getHttpsCallable("ludoRollDice")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.getData() as Map<String, Any>

            if (response["success"] == true) {
                Result.success(
                    DiceRollResult(
                        diceValue = (response["diceValue"] as Number).toInt(),
                        canRollAgain = response["canRollAgain"] as Boolean,
                        mustSelectToken = response["mustSelectToken"] as Boolean,
                        skipTurn = response["skipTurn"] as Boolean,
                        consecutiveSixes = (response["consecutiveSixes"] as Number).toInt(),
                        reason = response["reason"] as? String
                    )
                )
            } else {
                Result.failure(LudoGameException("Failed to roll dice"))
            }
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    /**
     * Move a token on the board.
     * Server validates the move and updates game state.
     *
     * @param roomId The game room ID
     * @param tokenId The token to move (0-3)
     * @return Result containing move outcome
     */
    suspend fun moveToken(roomId: String, tokenId: Int): Result<MoveTokenResult> {
        return try {
            val data = hashMapOf(
                "roomId" to roomId,
                "tokenId" to tokenId
            )

            val result = functions
                .getHttpsCallable("ludoMoveToken")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.getData() as Map<String, Any>

            if (response["success"] == true) {
                @Suppress("UNCHECKED_CAST")
                val moveData = response["move"] as Map<String, Any>

                Result.success(
                    MoveTokenResult(
                        move = parseLudoMove(moveData),
                        bonusTurn = response["bonusTurn"] as Boolean,
                        bonusReason = response["bonusReason"] as? String,
                        hasWon = response["hasWon"] as Boolean,
                        gameStatus = parseGameStatus(response["gameStatus"] as String)
                    )
                )
            } else {
                Result.failure(LudoGameException("Failed to move token"))
            }
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    /**
     * Create a new Ludo game room.
     *
     * @param playerCount Number of players (2-4)
     * @param isPrivate Whether the room is private
     * @return Result containing room ID and player color
     */
    suspend fun createGame(playerCount: Int, isPrivate: Boolean = false): Result<CreateGameResult> {
        return try {
            val data = hashMapOf(
                "playerCount" to playerCount,
                "isPrivate" to isPrivate
            )

            val result = functions
                .getHttpsCallable("ludoCreateGame")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.getData() as Map<String, Any>

            if (response["success"] == true) {
                Result.success(
                    CreateGameResult(
                        roomId = response["roomId"] as String,
                        playerColor = LudoColor.valueOf(response["playerColor"] as String)
                    )
                )
            } else {
                Result.failure(LudoGameException("Failed to create game"))
            }
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    /**
     * Parse a LudoMove from Firebase response data.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseLudoMove(data: Map<String, Any>): LudoMove {
        val capturedInfo = (data["capturedTokenInfo"] as? Map<String, Any>)?.let {
            CapturedTokenInfo(
                playerId = it["playerId"] as String,
                tokenId = (it["tokenId"] as Number).toInt(),
                position = (it["position"] as Number).toInt()
            )
        }

        return LudoMove(
            playerId = data["playerId"] as String,
            tokenId = (data["tokenId"] as Number).toInt(),
            diceValue = (data["diceValue"] as Number).toInt(),
            fromPosition = (data["fromPosition"] as Number).toInt(),
            toPosition = (data["toPosition"] as Number).toInt(),
            moveType = MoveType.valueOf(data["moveType"] as String),
            capturedTokenInfo = capturedInfo,
            timestamp = (data["timestamp"] as Number).toLong()
        )
    }

    /**
     * Parse GameStatus from string.
     */
    private fun parseGameStatus(status: String): GameStatus {
        return try {
            GameStatus.valueOf(status)
        } catch (e: Exception) {
            GameStatus.IN_PROGRESS
        }
    }

    /**
     * Map Firebase exceptions to user-friendly errors.
     */
    private fun mapFirebaseException(e: Exception): LudoGameException {
        val message = when (e) {
            is FirebaseFunctionsException -> {
                when (e.code) {
                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        "You must be signed in to play."
                    FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                        "It's not your turn."
                    FirebaseFunctionsException.Code.NOT_FOUND ->
                        "Game not found."
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                        e.message ?: "Invalid action."
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                        e.message ?: "Invalid move."
                    else -> e.message ?: "An error occurred."
                }
            }
            else -> e.message ?: "An error occurred."
        }
        return LudoGameException(message.trim(), e)
    }
}

/**
 * Result of a dice roll operation.
 */
data class DiceRollResult(
    val diceValue: Int,
    val canRollAgain: Boolean,
    val mustSelectToken: Boolean,
    val skipTurn: Boolean,
    val consecutiveSixes: Int,
    val reason: String?
)

/**
 * Result of a move token operation.
 */
data class MoveTokenResult(
    val move: LudoMove,
    val bonusTurn: Boolean,
    val bonusReason: String?,
    val hasWon: Boolean,
    val gameStatus: GameStatus
)

/**
 * Result of creating a new game.
 */
data class CreateGameResult(
    val roomId: String,
    val playerColor: LudoColor
)

/**
 * Exception for Ludo game errors.
 */
class LudoGameException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
