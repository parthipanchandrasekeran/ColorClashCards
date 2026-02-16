package com.parthipan.colorclashcards.voice

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore signaling layer for WebRTC voice chat.
 * Game-agnostic: operates on any [roomPath] (e.g. "rooms/abc" or "ludoRooms/xyz").
 */
class VoiceChatSignaling(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val listeners = mutableListOf<ListenerRegistration>()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    // ── Participants ────────────────────────────────────────────────

    suspend fun joinAsParticipant(roomPath: String, displayName: String) {
        val doc = VoiceChatParticipant(displayName = displayName, isMuted = false)
        db.document("$roomPath/voiceChat/participants/$currentUserId")
            .set(doc)
            .await()
    }

    suspend fun leaveAsParticipant(roomPath: String) {
        db.document("$roomPath/voiceChat/participants/$currentUserId")
            .delete()
            .await()
    }

    suspend fun updateMuteStatus(roomPath: String, isMuted: Boolean) {
        db.document("$roomPath/voiceChat/participants/$currentUserId")
            .update("isMuted", isMuted)
            .await()
    }

    /** Observe participant list changes. Emits full map on each change. */
    fun observeParticipants(roomPath: String): Flow<Map<String, VoiceChatParticipant>> =
        callbackFlow {
            val reg = db.collection("$roomPath/voiceChat/participants")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val map = snapshot?.documents?.associate { doc ->
                        doc.id to (doc.toObject(VoiceChatParticipant::class.java)
                            ?: VoiceChatParticipant())
                    } ?: emptyMap()
                    trySend(map)
                }
            listeners.add(reg)
            awaitClose { reg.remove() }
        }

    // ── SDP Offer / Answer ──────────────────────────────────────────

    suspend fun sendOffer(roomPath: String, pairId: String, sdp: String) {
        db.document("$roomPath/voicePeers/$pairId")
            .set(mapOf("offer" to mapOf("type" to "offer", "sdp" to sdp)))
            .await()
    }

    suspend fun sendAnswer(roomPath: String, pairId: String, sdp: String) {
        db.document("$roomPath/voicePeers/$pairId")
            .update("answer", mapOf("type" to "answer", "sdp" to sdp))
            .await()
    }

    /** Observe SDP answer appearing for an offer we sent. */
    fun observeAnswer(roomPath: String, pairId: String): Flow<String?> = callbackFlow {
        val reg = db.document("$roomPath/voicePeers/$pairId")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val answer = (snapshot?.get("answer") as? Map<String, Any>)?.get("sdp") as? String
                trySend(answer)
            }
        listeners.add(reg)
        awaitClose { reg.remove() }
    }

    /** Observe SDP offer arriving for us. */
    fun observeOffer(roomPath: String, pairId: String): Flow<String?> = callbackFlow {
        val reg = db.document("$roomPath/voicePeers/$pairId")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val offer = (snapshot?.get("offer") as? Map<String, Any>)?.get("sdp") as? String
                trySend(offer)
            }
        listeners.add(reg)
        awaitClose { reg.remove() }
    }

    // ── ICE Candidates ──────────────────────────────────────────────

    suspend fun addIceCandidate(
        roomPath: String,
        pairId: String,
        isOfferer: Boolean,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        val collection = if (isOfferer) "offerCandidates" else "answerCandidates"
        db.collection("$roomPath/voicePeers/$pairId/$collection")
            .add(
                mapOf(
                    "candidate" to candidate,
                    "sdpMid" to sdpMid,
                    "sdpMLineIndex" to sdpMLineIndex
                )
            )
            .await()
    }

    /** Observe remote ICE candidates. */
    fun observeIceCandidates(
        roomPath: String,
        pairId: String,
        isOfferer: Boolean
    ): Flow<List<IceCandidateData>> = callbackFlow {
        // We listen to the OTHER side's candidates
        val collection = if (isOfferer) "answerCandidates" else "offerCandidates"
        val reg = db.collection("$roomPath/voicePeers/$pairId/$collection")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val candidates = snapshot?.documents?.map { doc ->
                    IceCandidateData(
                        candidate = doc.getString("candidate") ?: "",
                        sdpMid = doc.getString("sdpMid"),
                        sdpMLineIndex = (doc.getLong("sdpMLineIndex") ?: 0).toInt()
                    )
                } ?: emptyList()
                trySend(candidates)
            }
        listeners.add(reg)
        awaitClose { reg.remove() }
    }

    // ── Cleanup ─────────────────────────────────────────────────────

    /** Delete the peer document and its subcollections for a pair. */
    suspend fun deletePeerData(roomPath: String, pairId: String) {
        val peerRef = db.document("$roomPath/voicePeers/$pairId")
        // Delete subcollections first
        for (sub in listOf("offerCandidates", "answerCandidates")) {
            val docs = peerRef.collection(sub).get().await()
            for (doc in docs) {
                doc.reference.delete().await()
            }
        }
        peerRef.delete().await()
    }

    /** Remove all Firestore listeners tracked by this instance. */
    fun removeAllListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    companion object {
        /** Deterministic pair ID so both peers use the same document. */
        fun pairId(uid1: String, uid2: String): String {
            val a = minOf(uid1, uid2)
            val b = maxOf(uid1, uid2)
            return "${a}_${b}"
        }

        /** Lower UID is always the offerer. */
        fun isOfferer(localUid: String, remoteUid: String): Boolean =
            localUid < remoteUid
    }
}

data class IceCandidateData(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)
