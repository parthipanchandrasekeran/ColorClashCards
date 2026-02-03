package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.parthipan.colorclashcards.game.ludo.model.LudoColor

/**
 * Represents a Ludo game room in Firestore.
 * Stored at: ludoRooms/{roomId}
 */
data class LudoRoom(
    @DocumentId
    val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val roomCode: String = "",
    val status: String = LudoRoomStatus.WAITING.name,
    val maxPlayers: Int = 4,
    val isPublic: Boolean = true,
    val players: List<LudoRoomPlayer> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun isFull(): Boolean = players.size >= maxPlayers

    fun getPlayerColor(playerId: String): LudoColor? {
        return players.find { it.odId == playerId }?.color?.let {
            try { LudoColor.valueOf(it) } catch (e: Exception) { null }
        }
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): LudoRoom {
            @Suppress("UNCHECKED_CAST")
            val playersData = map["players"] as? List<Map<String, Any?>> ?: emptyList()
            return LudoRoom(
                id = id,
                hostId = map["hostId"] as? String ?: "",
                hostName = map["hostName"] as? String ?: "",
                roomCode = map["roomCode"] as? String ?: "",
                status = map["status"] as? String ?: LudoRoomStatus.WAITING.name,
                maxPlayers = (map["maxPlayers"] as? Number)?.toInt() ?: 4,
                isPublic = map["isPublic"] as? Boolean ?: true,
                players = playersData.map { LudoRoomPlayer.fromMap(it) },
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "hostId" to hostId,
        "hostName" to hostName,
        "roomCode" to roomCode,
        "status" to status,
        "maxPlayers" to maxPlayers,
        "isPublic" to isPublic,
        "players" to players.map { it.toMap() },
        "createdAt" to createdAt
    )
}

/**
 * Room status for Ludo games.
 */
enum class LudoRoomStatus {
    WAITING,    // Waiting for players
    PLAYING,    // Game in progress
    ENDED       // Game finished
}

/**
 * A player in a Ludo room.
 */
data class LudoRoomPlayer(
    val odId: String = "",
    val odisplayName: String = "",
    val photoUrl: String? = null,
    val color: String = "",         // LudoColor name
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val isConnected: Boolean = true,
    val lastActiveAt: Timestamp? = null,
    val disconnectedAt: Timestamp? = null,
    val joinedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): LudoRoomPlayer {
            return LudoRoomPlayer(
                odId = map["odId"] as? String ?: "",
                odisplayName = map["odisplayName"] as? String ?: "",
                photoUrl = map["photoUrl"] as? String,
                color = map["color"] as? String ?: "",
                isReady = map["isReady"] as? Boolean ?: false,
                isHost = map["isHost"] as? Boolean ?: false,
                isConnected = map["isConnected"] as? Boolean ?: true,
                lastActiveAt = map["lastActiveAt"] as? Timestamp,
                disconnectedAt = map["disconnectedAt"] as? Timestamp,
                joinedAt = map["joinedAt"] as? Timestamp
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "odId" to odId,
        "odisplayName" to odisplayName,
        "photoUrl" to photoUrl,
        "color" to color,
        "isReady" to isReady,
        "isHost" to isHost,
        "isConnected" to isConnected,
        "lastActiveAt" to lastActiveAt,
        "disconnectedAt" to disconnectedAt,
        "joinedAt" to joinedAt
    )
}

/**
 * Active Ludo room info for reconnection.
 */
data class ActiveLudoRoomInfo(
    val roomId: String,
    val isHost: Boolean
)
