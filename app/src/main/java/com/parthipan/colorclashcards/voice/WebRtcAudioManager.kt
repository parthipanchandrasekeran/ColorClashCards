package com.parthipan.colorclashcards.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
import org.webrtc.RtpTransceiver
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
            // Multiple STUN servers for reliability
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            // TURN servers (Metered free relay)
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
    private var audioFocusRequest: AudioFocusRequest? = null

    /** PeerConnection per remote userId. */
    private val connections = mutableMapOf<String, PeerConnection>()

    /** Track already-added ICE candidates per peer to prevent duplicates from Firestore snapshots. */
    private val addedCandidates = mutableMapOf<String, MutableSet<String>>()

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

        // Empty constraints for audio source (OfferToReceiveAudio is an SDP constraint, not audio source)
        audioSource = factory!!.createAudioSource(MediaConstraints())
        localAudioTrack = factory!!.createAudioTrack(LOCAL_TRACK_ID, audioSource).apply {
            setEnabled(true)
        }

        // Request audio focus for voice communication
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousAudioMode = audioManager.mode
        previousSpeakerOn = audioManager.isSpeakerphoneOn

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
            audioFocusRequest = focusRequest
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN
            )
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        Log.d(TAG, "WebRTC initialized, local audio track created, audio focus acquired")
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

            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d(TAG, "Signaling state for $remoteUserId: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "ICE gathering for $remoteUserId: $state")
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

            // Deprecated with UNIFIED_PLAN but kept as fallback
            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "onAddStream from $remoteUserId (audio tracks: ${stream.audioTracks.size})")
                stream.audioTracks.forEach {
                    it.setEnabled(true)
                    Log.d(TAG, "Enabled remote audio track via onAddStream for $remoteUserId")
                }
            }
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}

            // UNIFIED_PLAN delivers remote tracks via onTrack
            override fun onTrack(transceiver: RtpTransceiver) {
                val remoteTrack = transceiver.receiver?.track()
                Log.d(TAG, "onTrack from $remoteUserId: kind=${remoteTrack?.kind()}, enabled=${remoteTrack?.enabled()}")
                if (remoteTrack is AudioTrack) {
                    remoteTrack.setEnabled(true)
                    Log.d(TAG, "Enabled remote audio track via onTrack for $remoteUserId")
                }
            }
        }

        val pc = f.createPeerConnection(rtcConfig, observer) ?: return null

        // Add local audio track
        pc.addTrack(track, listOf(LOCAL_STREAM_ID))

        connections[remoteUserId] = pc
        addedCandidates[remoteUserId] = mutableSetOf()
        Log.d(TAG, "PeerConnection created for $remoteUserId")
        return pc
    }

    /**
     * Backup method to enable remote audio tracks via transceivers.
     * Call after setRemoteDescription in case onTrack/onAddStream didn't fire.
     */
    fun enableRemoteAudio(remoteUserId: String) {
        val pc = connections[remoteUserId] ?: return
        try {
            pc.transceivers?.forEach { transceiver ->
                val track = transceiver.receiver?.track()
                if (track is AudioTrack && track.id() != LOCAL_TRACK_ID) {
                    track.setEnabled(true)
                    Log.d(TAG, "Enabled remote audio via transceiver for $remoteUserId")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable remote audio via transceivers for $remoteUserId", e)
        }
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
        Log.d(TAG, "Remote description set for $remoteUserId (type=$type)")
    }

    /** Add a remote ICE candidate. Deduplicates against already-added candidates. */
    fun addRemoteIceCandidate(remoteUserId: String, candidate: IceCandidate): Boolean {
        val key = "${candidate.sdpMid}:${candidate.sdpMLineIndex}:${candidate.sdp}"
        val added = addedCandidates[remoteUserId] ?: return false
        if (!added.add(key)) {
            return false // Already added, skip
        }
        connections[remoteUserId]?.addIceCandidate(candidate)
        return true
    }

    // ── Mute ────────────────────────────────────────────────────────

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        Log.d(TAG, "Local audio track muted=$muted, enabled=${!muted}")
    }

    // ── Cleanup ─────────────────────────────────────────────────────

    /** Close a single peer connection. */
    fun closeConnection(remoteUserId: String) {
        connections.remove(remoteUserId)?.apply {
            close()
            dispose()
        }
        addedCandidates.remove(remoteUserId)
        Log.d(TAG, "Connection closed for $remoteUserId")
    }

    /** Close all connections and release resources. */
    fun release() {
        connections.values.forEach { pc ->
            pc.close()
            pc.dispose()
        }
        connections.clear()
        addedCandidates.clear()
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        factory?.dispose()
        factory = null

        // Abandon audio focus and restore AudioManager state
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeakerOn

        Log.d(TAG, "WebRTC resources released, audio focus abandoned")
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
