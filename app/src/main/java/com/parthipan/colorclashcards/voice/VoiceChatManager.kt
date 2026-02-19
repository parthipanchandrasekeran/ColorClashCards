package com.parthipan.colorclashcards.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * High-level coordinator for voice chat.
 * Ties [VoiceChatSignaling] (Firestore) and [WebRtcAudioManager] (WebRTC) together.
 */
class VoiceChatManager(
    context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceChatManager"
    }

    private val signaling = VoiceChatSignaling()
    private val webRtc = WebRtcAudioManager(context)

    private val _uiState = MutableStateFlow(VoiceChatUiState())
    val uiState: StateFlow<VoiceChatUiState> = _uiState.asStateFlow()

    private var roomPath: String? = null
    private var participantObserverJob: Job? = null

    /** Track which remote peers we've already established connections with. */
    private val connectedPeers = mutableSetOf<String>()

    /** Track ICE candidate observer jobs per peer. */
    private val peerJobs = mutableMapOf<String, List<Job>>()

    // ── Public API ──────────────────────────────────────────────────

    fun joinVoiceChat(roomPath: String, displayName: String) {
        if (_uiState.value.status != VoiceChatStatus.DISCONNECTED) return
        this.roomPath = roomPath

        _uiState.update { it.copy(status = VoiceChatStatus.CONNECTING, error = null) }

        scope.launch {
            try {
                // 1. Initialize WebRTC
                webRtc.initialize()

                // 2. Set up ICE candidate callback
                webRtc.onIceCandidate = { remoteUserId, candidate ->
                    scope.launch {
                        sendIceCandidate(remoteUserId, candidate)
                    }
                }

                webRtc.onConnectionStateChange = { remoteUserId, state ->
                    Log.d(TAG, "Connection to $remoteUserId: $state")
                    if (state == PeerConnection.IceConnectionState.CONNECTED ||
                        state == PeerConnection.IceConnectionState.COMPLETED
                    ) {
                        _uiState.update { it.copy(status = VoiceChatStatus.CONNECTED) }
                    }
                }

                // 3. Register as participant in Firestore
                signaling.joinAsParticipant(roomPath, displayName)

                // 4. Observe participants → connect to new ones, disconnect from removed
                participantObserverJob = scope.launch {
                    signaling.observeParticipants(roomPath).collect { participants ->
                        _uiState.update { it.copy(participants = participants) }
                        handleParticipantChanges(participants)
                    }
                }

                // If no other participants yet, we're still "connected" (just alone)
                _uiState.update {
                    it.copy(
                        status = if (connectedPeers.isEmpty()) VoiceChatStatus.CONNECTED
                        else it.status
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join voice chat", e)
                _uiState.update {
                    it.copy(
                        status = VoiceChatStatus.DISCONNECTED,
                        error = "Failed to join voice: ${e.message}"
                    )
                }
            }
        }
    }

    fun leaveVoiceChat() {
        val path = roomPath ?: return

        scope.launch {
            try {
                // Remove participant doc
                signaling.leaveAsParticipant(path)

                // Clean up peer data for all our connections
                val localUid = signaling.currentUserId
                for (remoteUid in connectedPeers.toList()) {
                    val pairId = VoiceChatSignaling.pairId(localUid, remoteUid)
                    try {
                        signaling.deletePeerData(path, pairId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clean peer data for $pairId", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during Firestore cleanup", e)
            }
        }

        // Cancel all observer jobs
        participantObserverJob?.cancel()
        peerJobs.values.flatten().forEach { it.cancel() }
        peerJobs.clear()
        connectedPeers.clear()

        // Release WebRTC
        webRtc.release()
        signaling.removeAllListeners()

        roomPath = null
        _uiState.update {
            VoiceChatUiState(status = VoiceChatStatus.DISCONNECTED)
        }
    }

    fun toggleMute() {
        val path = roomPath ?: return
        val newMuted = !_uiState.value.isMuted
        webRtc.setMuted(newMuted)
        _uiState.update { it.copy(isMuted = newMuted) }

        scope.launch {
            try {
                signaling.updateMuteStatus(path, newMuted)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update mute status in Firestore", e)
            }
        }
    }

    // ── Internal ────────────────────────────────────────────────────

    private fun handleParticipantChanges(participants: Map<String, VoiceChatParticipant>) {
        val localUid = signaling.currentUserId
        val remoteUids = participants.keys.filter { it != localUid }.toSet()

        // New peers to connect to
        val newPeers = remoteUids - connectedPeers
        // Peers that left
        val removedPeers = connectedPeers - remoteUids

        for (remoteUid in removedPeers) {
            disconnectPeer(remoteUid)
        }

        for (remoteUid in newPeers) {
            connectToPeer(remoteUid)
        }
    }

    private fun connectToPeer(remoteUid: String) {
        val path = roomPath ?: return
        val localUid = signaling.currentUserId
        val pairId = VoiceChatSignaling.pairId(localUid, remoteUid)
        val isOfferer = VoiceChatSignaling.isOfferer(localUid, remoteUid)

        connectedPeers.add(remoteUid)

        // Create PeerConnection
        webRtc.createConnection(remoteUid) ?: run {
            Log.e(TAG, "Failed to create PeerConnection for $remoteUid")
            connectedPeers.remove(remoteUid)
            return
        }

        val jobs = mutableListOf<Job>()

        if (isOfferer) {
            // We create the offer
            jobs += scope.launch {
                try {
                    val offer = webRtc.createOffer(remoteUid) ?: return@launch
                    signaling.sendOffer(path, pairId, offer.description)
                    Log.d(TAG, "Offer sent for $remoteUid")

                    // Wait for answer (process only once)
                    var answerProcessed = false
                    signaling.observeAnswer(path, pairId).collect { answerSdp ->
                        if (answerSdp != null && !answerProcessed) {
                            answerProcessed = true
                            webRtc.setRemoteDescription(
                                remoteUid,
                                SessionDescription.Type.ANSWER,
                                answerSdp
                            )
                            // Ensure remote audio tracks are enabled
                            webRtc.enableRemoteAudio(remoteUid)
                            Log.d(TAG, "Answer received and set for $remoteUid")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Offer flow failed for $remoteUid", e)
                }
            }
        } else {
            // We wait for the offer and send an answer
            jobs += scope.launch {
                try {
                    var offerProcessed = false
                    signaling.observeOffer(path, pairId).collect { offerSdp ->
                        if (offerSdp != null && !offerProcessed) {
                            offerProcessed = true
                            webRtc.setRemoteDescription(
                                remoteUid,
                                SessionDescription.Type.OFFER,
                                offerSdp
                            )
                            val answer = webRtc.createAnswer(remoteUid) ?: return@collect
                            signaling.sendAnswer(path, pairId, answer.description)
                            // Ensure remote audio tracks are enabled
                            webRtc.enableRemoteAudio(remoteUid)
                            Log.d(TAG, "Offer received, answer sent for $remoteUid")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Answer flow failed for $remoteUid", e)
                }
            }
        }

        // Observe remote ICE candidates
        jobs += scope.launch {
            try {
                signaling.observeIceCandidates(path, pairId, isOfferer).collect { candidates ->
                    for (c in candidates) {
                        val iceCandidate = IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
                        // addRemoteIceCandidate deduplicates internally
                        webRtc.addRemoteIceCandidate(remoteUid, iceCandidate)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ICE candidate observation failed for $remoteUid", e)
            }
        }

        peerJobs[remoteUid] = jobs
    }

    private fun disconnectPeer(remoteUid: String) {
        connectedPeers.remove(remoteUid)
        peerJobs.remove(remoteUid)?.forEach { it.cancel() }
        webRtc.closeConnection(remoteUid)
        Log.d(TAG, "Disconnected from $remoteUid")
    }

    private suspend fun sendIceCandidate(remoteUserId: String, candidate: IceCandidate) {
        val path = roomPath ?: return
        val localUid = signaling.currentUserId
        val pairId = VoiceChatSignaling.pairId(localUid, remoteUserId)
        val isOfferer = VoiceChatSignaling.isOfferer(localUid, remoteUserId)

        try {
            signaling.addIceCandidate(
                roomPath = path,
                pairId = pairId,
                isOfferer = isOfferer,
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send ICE candidate", e)
        }
    }
}
