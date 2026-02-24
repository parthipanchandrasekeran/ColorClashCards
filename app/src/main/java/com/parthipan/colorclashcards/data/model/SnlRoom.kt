package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a Snake & Ladder game room in Firestore.
 * Stored at: snlRooms/{roomId}
 */
data class SnlRoom(
    @DocumentId
    val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val roomCode: String = "",
    val status: String = SnlRoomStatus.WAITING.name,
    val maxPlayers: Int = 4,
    val isPublic: Boolean = true,
    val boardLayout: String = "CLASSIC",
    val players: List<SnlRoomPlayer> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun isFull(): Boolean = players.size >= maxPlayers

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): SnlRoom {
            @Suppress("UNCHECKED_CAST")
            val playersData = map["players"] as? List<Map<String, Any?>> ?: emptyList()
            return SnlRoom(
                id = id,
                hostId = map["hostId"] as? String ?: "",
                hostName = map["hostName"] as? String ?: "",
                roomCode = map["roomCode"] as? String ?: "",
                status = map["status"] as? String ?: SnlRoomStatus.WAITING.name,
                maxPlayers = (map["maxPlayers"] as? Number)?.toInt() ?: 4,
                isPublic = map["isPublic"] as? Boolean ?: true,
                boardLayout = map["boardLayout"] as? String ?: "CLASSIC",
                players = playersData.map { SnlRoomPlayer.fromMap(it) },
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
        "boardLayout" to boardLayout,
        "players" to players.map { it.toMap() },
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp())
    )
}

enum class SnlRoomStatus {
    WAITING,
    PLAYING,
    ENDED
}

/**
 * A player in a SNL room.
 */
data class SnlRoomPlayer(
    val odId: String = "",
    val odisplayName: String = "",
    val photoUrl: String? = null,
    val colorIndex: Int = 0,
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val isConnected: Boolean = true,
    val lastActiveAt: Timestamp? = null,
    val disconnectedAt: Timestamp? = null,
    val joinedAt: Timestamp? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): SnlRoomPlayer {
            return SnlRoomPlayer(
                odId = map["odId"] as? String ?: "",
                odisplayName = map["odisplayName"] as? String ?: "",
                photoUrl = map["photoUrl"] as? String,
                colorIndex = (map["colorIndex"] as? Number)?.toInt() ?: 0,
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
        "colorIndex" to colorIndex,
        "isReady" to isReady,
        "isHost" to isHost,
        "isConnected" to isConnected,
        "lastActiveAt" to lastActiveAt,
        "disconnectedAt" to disconnectedAt,
        "joinedAt" to joinedAt
    )
}

/**
 * Active SNL room info for reconnection.
 */
data class ActiveSnlRoomInfo(
    val roomId: String,
    val isHost: Boolean
)
