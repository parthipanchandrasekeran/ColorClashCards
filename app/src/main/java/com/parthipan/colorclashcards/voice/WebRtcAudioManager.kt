package com.parthipan.colorclashcards.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Manages WebRTC peer connections for audio-only voice chat.
 * Shared local audio track across all connections; one PeerConnection per remote peer.
 */
class WebRtcAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcAudioManager"
        private const val LOCAL_TRACK_ID = "voice_local_audio"
        private const val LOCAL_STREAM_ID = "voice_local_stream"
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        )
    }

    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerOn: Boolean = false

    /** PeerConnection per remote userId. */
    private val connections = mutableMapOf<String, PeerConnection>()

    /** Callback for locally-generated ICE candidates. (remoteUserId, candidate) */
    var onIceCandidate: ((String, IceCandidate) -> Unit)? = null

    /** Callback when ICE connection state changes. (remoteUserId, state) */
    var onConnectionStateChange: ((String, PeerConnection.IceConnectionState) -> Unit)? = null

    // ── Initialization ──────────────────────────────────────────────

    /** Initialize PeerConnectionFactory and local audio. Call once from IO thread. */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (factory != null) return@withContext

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("")
                .createInitializationOptions()
        )

        val eglBase = EglBase.create()

        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        audioSource = factory!!.createAudioSource(constraints)
        localAudioTrack = factory!!.createAudioTrack(LOCAL_TRACK_ID, audioSource).apply {
            setEnabled(true)
        }

        // Configure AudioManager for VoIP
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousAudioMode = audioManager.mode
        previousSpeakerOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        Log.d(TAG, "WebRTC initialized, local audio track created")
    }

    // ── Peer Connections ────────────────────────────────────────────

    /** Create a PeerConnection for a remote user. */
    fun createConnection(remoteUserId: String): PeerConnection? {
        val f = factory ?: return null
        val track = localAudioTrack ?: return null

        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE candidate for $remoteUserId: ${candidate.sdpMid}")
                onIceCandidate?.invoke(remoteUserId, candidate)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE state for $remoteUserId: $state")
                onConnectionStateChange?.invoke(remoteUserId, state)
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "Remote stream added from $remoteUserId (tracks: ${stream.audioTracks.size})")
                stream.audioTracks.forEach { it.setEnabled(true) }
            }
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }

        val pc = f.createPeerConnection(rtcConfig, observer) ?: return null

        // Add local audio track
        pc.addTrack(track, listOf(LOCAL_STREAM_ID))

        connections[remoteUserId] = pc
        Log.d(TAG, "PeerConnection created for $remoteUserId")
        return pc
    }

    /** Create an SDP offer. */
    suspend fun createOffer(remoteUserId: String): SessionDescription? {
        val pc = connections[remoteUserId] ?: return null
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        val sdp = suspendSdpCreate { pc.createOffer(it, constraints) }
        pc.setLocalDescription(sdp)
        return sdp
    }

    /** Create an SDP answer. */
    suspend fun createAnswer(remoteUserId: String): SessionDescription? {
        val pc = connections[remoteUserId] ?: return null
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        val sdp = suspendSdpCreate { pc.createAnswer(it, constraints) }
        pc.setLocalDescription(sdp)
        return sdp
    }

    /** Set remote SDP (offer or answer). */
    suspend fun setRemoteDescription(remoteUserId: String, type: SessionDescription.Type, sdp: String) {
        val pc = connections[remoteUserId] ?: return
        val desc = SessionDescription(type, sdp)
        suspendSdpSet { pc.setRemoteDescription(it, desc) }
    }

    /** Add a remote ICE candidate. */
    fun addRemoteIceCandidate(remoteUserId: String, candidate: IceCandidate) {
        connections[remoteUserId]?.addIceCandidate(candidate)
    }

    // ── Mute ────────────────────────────────────────────────────────

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    // ── Cleanup ─────────────────────────────────────────────────────

    /** Close a single peer connection. */
    fun closeConnection(remoteUserId: String) {
        connections.remove(remoteUserId)?.apply {
            close()
            dispose()
        }
        Log.d(TAG, "Connection closed for $remoteUserId")
    }

    /** Close all connections and release resources. */
    fun release() {
        connections.values.forEach { pc ->
            pc.close()
            pc.dispose()
        }
        connections.clear()
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        factory?.dispose()
        factory = null

        // Restore AudioManager state
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeakerOn

        Log.d(TAG, "WebRTC resources released")
    }

    // ── SDP Helpers ─────────────────────────────────────────────────

    private suspend fun suspendSdpCreate(
        action: (SdpObserver) -> Unit
    ): SessionDescription = suspendCoroutine { cont ->
        action(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
            override fun onCreateFailure(error: String) { cont.resumeWithException(RuntimeException(error)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        })
    }

    private suspend fun suspendSdpSet(
        action: (SdpObserver) -> Unit
    ): Unit = suspendCoroutine { cont ->
        action(object : SdpObserver {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String) { cont.resumeWithException(RuntimeException(error)) }
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        })
    }

    private suspend fun PeerConnection.setLocalDescription(sdp: SessionDescription) {
        suspendSdpSet { setLocalDescription(it, sdp) }
    }
}
