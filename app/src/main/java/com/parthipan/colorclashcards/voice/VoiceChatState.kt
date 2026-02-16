package com.parthipan.colorclashcards.voice

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** Voice chat connection status. */
enum class VoiceChatStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/** Firestore-serializable participant document. */
data class VoiceChatParticipant(
    val displayName: String = "",
    val isMuted: Boolean = false,
    @ServerTimestamp val joinedAt: Date? = null
)

/** UI state exposed to composables. */
data class VoiceChatUiState(
    val status: VoiceChatStatus = VoiceChatStatus.DISCONNECTED,
    val isMuted: Boolean = false,
    val participants: Map<String, VoiceChatParticipant> = emptyMap(),
    val error: String? = null
)
