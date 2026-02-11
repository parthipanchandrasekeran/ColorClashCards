package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for LudoRoom model — fromMap() parsing and toMap() serialization.
 *
 * Note: toMap() with null createdAt uses FieldValue.serverTimestamp(), which
 * requires Firebase native code. We test the conditional behavior indirectly
 * by verifying that a non-null Timestamp is preserved, and that fromMap()
 * correctly parses all fields.
 */
class LudoRoomTest {

    // ==================== fromMap() TESTS ====================

    @Test
    fun `fromMap with all fields parses correctly`() {
        val now = Timestamp.now()
        val map = mapOf<String, Any?>(
            "hostId" to "host-123",
            "hostName" to "Alice",
            "roomCode" to "ABCD",
            "status" to "PLAYING",
            "maxPlayers" to 4L, // Firestore returns Long
            "isPublic" to true,
            "players" to listOf(
                mapOf<String, Any?>(
                    "odId" to "player-1",
                    "odisplayName" to "Alice",
                    "color" to "RED",
                    "isReady" to true,
                    "isHost" to true,
                    "isConnected" to true,
                    "photoUrl" to null,
                    "lastActiveAt" to null,
                    "disconnectedAt" to null,
                    "joinedAt" to null
                )
            ),
            "createdAt" to now
        )

        val room = LudoRoom.fromMap("room-1", map)

        assertEquals("room-1", room.id)
        assertEquals("host-123", room.hostId)
        assertEquals("Alice", room.hostName)
        assertEquals("ABCD", room.roomCode)
        assertEquals("PLAYING", room.status)
        assertEquals(4, room.maxPlayers)
        assertTrue(room.isPublic)
        assertEquals(1, room.players.size)
        assertEquals("player-1", room.players[0].odId)
        assertEquals("Alice", room.players[0].odisplayName)
        assertEquals("RED", room.players[0].color)
        assertTrue(room.players[0].isReady)
        assertTrue(room.players[0].isHost)
        assertEquals(now, room.createdAt)
    }

    @Test
    fun `fromMap with missing fields uses defaults`() {
        val map = emptyMap<String, Any?>()

        val room = LudoRoom.fromMap("room-2", map)

        assertEquals("room-2", room.id)
        assertEquals("", room.hostId)
        assertEquals("", room.hostName)
        assertEquals("", room.roomCode)
        assertEquals(LudoRoomStatus.WAITING.name, room.status)
        assertEquals(4, room.maxPlayers)
        assertTrue(room.isPublic)
        assertTrue(room.players.isEmpty())
        assertNull(room.createdAt)
    }

    @Test
    fun `fromMap with null createdAt sets null`() {
        val map = mapOf<String, Any?>(
            "hostId" to "host-1",
            "createdAt" to null
        )

        val room = LudoRoom.fromMap("room-3", map)

        assertNull(room.createdAt)
    }

    // ==================== toMap() TESTS ====================

    @Test
    fun `toMap with existing createdAt preserves timestamp`() {
        val now = Timestamp.now()
        val room = LudoRoom(
            id = "room-1",
            hostId = "host-123",
            hostName = "Bob",
            roomCode = "WXYZ",
            status = LudoRoomStatus.WAITING.name,
            maxPlayers = 2,
            isPublic = false,
            players = emptyList(),
            createdAt = now
        )

        val map = room.toMap()

        assertEquals("host-123", map["hostId"])
        assertEquals("Bob", map["hostName"])
        assertEquals("WXYZ", map["roomCode"])
        assertEquals(LudoRoomStatus.WAITING.name, map["status"])
        assertEquals(2, map["maxPlayers"])
        assertEquals(false, map["isPublic"])
        assertEquals(now, map["createdAt"])
    }

    @Test
    fun `toMap with null createdAt uses server timestamp sentinel`() {
        val room = LudoRoom(
            id = "room-1",
            hostId = "host-123",
            createdAt = null
        )

        val map = room.toMap()

        // When createdAt is null, toMap() returns FieldValue.serverTimestamp()
        // which is NOT a Timestamp — it's a sentinel FieldValue object
        val createdAtValue = map["createdAt"]
        assertNotNull("createdAt should not be null in map", createdAtValue)
        assertFalse(
            "null createdAt should produce FieldValue, not Timestamp",
            createdAtValue is Timestamp
        )
    }

    // ==================== fromMap/toMap ROUNDTRIP ====================

    @Test
    fun `fromMap then toMap preserves all fields when createdAt is set`() {
        val now = Timestamp.now()
        val originalMap = mapOf<String, Any?>(
            "hostId" to "host-abc",
            "hostName" to "Charlie",
            "roomCode" to "1234",
            "status" to "ENDED",
            "maxPlayers" to 3L,
            "isPublic" to false,
            "players" to listOf(
                mapOf<String, Any?>(
                    "odId" to "p1",
                    "odisplayName" to "Charlie",
                    "color" to "BLUE",
                    "isReady" to false,
                    "isHost" to true,
                    "isConnected" to true,
                    "photoUrl" to "https://example.com/photo.jpg",
                    "lastActiveAt" to null,
                    "disconnectedAt" to null,
                    "joinedAt" to null
                )
            ),
            "createdAt" to now
        )

        val room = LudoRoom.fromMap("room-rt", originalMap)
        val roundTripped = room.toMap()

        assertEquals(originalMap["hostId"], roundTripped["hostId"])
        assertEquals(originalMap["hostName"], roundTripped["hostName"])
        assertEquals(originalMap["roomCode"], roundTripped["roomCode"])
        assertEquals(originalMap["status"], roundTripped["status"])
        // maxPlayers: original is Long from Firestore, toMap produces Int
        assertEquals((originalMap["maxPlayers"] as Long).toInt(), roundTripped["maxPlayers"])
        assertEquals(originalMap["isPublic"], roundTripped["isPublic"])
        assertEquals(now, roundTripped["createdAt"])
    }

    // ==================== PLAYER PARSING ====================

    @Test
    fun `LudoRoomPlayer fromMap with all fields`() {
        val ts = Timestamp.now()
        val map = mapOf<String, Any?>(
            "odId" to "user-xyz",
            "odisplayName" to "Diana",
            "photoUrl" to "https://example.com/pic.jpg",
            "color" to "GREEN",
            "isReady" to true,
            "isHost" to false,
            "isConnected" to false,
            "lastActiveAt" to ts,
            "disconnectedAt" to ts,
            "joinedAt" to ts
        )

        val player = LudoRoomPlayer.fromMap(map)

        assertEquals("user-xyz", player.odId)
        assertEquals("Diana", player.odisplayName)
        assertEquals("https://example.com/pic.jpg", player.photoUrl)
        assertEquals("GREEN", player.color)
        assertTrue(player.isReady)
        assertFalse(player.isHost)
        assertFalse(player.isConnected)
        assertEquals(ts, player.lastActiveAt)
        assertEquals(ts, player.disconnectedAt)
        assertEquals(ts, player.joinedAt)
    }

    @Test
    fun `LudoRoomPlayer fromMap with missing fields uses defaults`() {
        val player = LudoRoomPlayer.fromMap(emptyMap())

        assertEquals("", player.odId)
        assertEquals("", player.odisplayName)
        assertNull(player.photoUrl)
        assertEquals("", player.color)
        assertFalse(player.isReady)
        assertFalse(player.isHost)
        assertTrue(player.isConnected) // default is true
    }

    // ==================== isFull() ====================

    @Test
    fun `room isFull returns true when at max capacity`() {
        val room = LudoRoom(
            maxPlayers = 2,
            players = listOf(
                LudoRoomPlayer(odId = "p1"),
                LudoRoomPlayer(odId = "p2")
            )
        )
        assertTrue(room.isFull())
    }

    @Test
    fun `room isFull returns false when under capacity`() {
        val room = LudoRoom(
            maxPlayers = 4,
            players = listOf(
                LudoRoomPlayer(odId = "p1")
            )
        )
        assertFalse(room.isFull())
    }

    // ==================== getPlayerColor() ====================

    @Test
    fun `getPlayerColor returns correct color for known player`() {
        val room = LudoRoom(
            players = listOf(
                LudoRoomPlayer(odId = "p1", color = "RED"),
                LudoRoomPlayer(odId = "p2", color = "BLUE")
            )
        )

        assertEquals(
            com.parthipan.colorclashcards.game.ludo.model.LudoColor.RED,
            room.getPlayerColor("p1")
        )
        assertEquals(
            com.parthipan.colorclashcards.game.ludo.model.LudoColor.BLUE,
            room.getPlayerColor("p2")
        )
    }

    @Test
    fun `getPlayerColor returns null for unknown player`() {
        val room = LudoRoom(
            players = listOf(
                LudoRoomPlayer(odId = "p1", color = "RED")
            )
        )

        assertNull(room.getPlayerColor("unknown-id"))
    }

    @Test
    fun `getPlayerColor returns null for invalid color string`() {
        val room = LudoRoom(
            players = listOf(
                LudoRoomPlayer(odId = "p1", color = "PURPLE") // not a valid LudoColor
            )
        )

        assertNull(room.getPlayerColor("p1"))
    }
}
