package com.parthipan.colorclashcards.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.view.View
import com.parthipan.colorclashcards.R
import com.parthipan.colorclashcards.data.preferences.AudioPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SoundManager(
    private val context: Context,
    private val audioPreferences: AudioPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val soundEnabled = MutableStateFlow(true)
    private val musicEnabled = MutableStateFlow(true)
    private val vibrationEnabled = MutableStateFlow(true)

    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<SoundEffect, Int>()

    private var mediaPlayer: MediaPlayer? = null
    private var currentMusicRes: Int = 0
    private var musicPaused = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()

        // Preload all sound effects
        SoundEffect.entries.forEach { effect ->
            soundIds[effect] = soundPool.load(context, effect.resId, 1)
        }

        // Collect preference flows
        scope.launch {
            audioPreferences.isSoundEnabled.collect { soundEnabled.value = it }
        }
        scope.launch {
            audioPreferences.isMusicEnabled.collect { enabled ->
                musicEnabled.value = enabled
                if (enabled) {
                    mediaPlayer?.let { if (!it.isPlaying && musicPaused) it.start() }
                } else {
                    mediaPlayer?.let { if (it.isPlaying) { it.pause(); musicPaused = true } }
                }
            }
        }
        scope.launch {
            audioPreferences.isVibrationEnabled.collect { vibrationEnabled.value = it }
        }
    }

    fun play(effect: SoundEffect) {
        if (!soundEnabled.value) return
        val id = soundIds[effect] ?: return
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun startMusic(musicRes: Int = R.raw.bg_music) {
        if (!musicEnabled.value) return
        if (mediaPlayer != null && currentMusicRes == musicRes) {
            // Already playing this track
            if (musicPaused) {
                mediaPlayer?.start()
                musicPaused = false
            }
            return
        }
        stopMusic()
        currentMusicRes = musicRes
        mediaPlayer = MediaPlayer.create(context, musicRes)?.apply {
            isLooping = true
            setVolume(0.3f, 0.3f)
            start()
        }
        musicPaused = false
    }

    fun pauseMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                musicPaused = true
            }
        }
    }

    fun resumeMusic() {
        if (!musicEnabled.value) return
        mediaPlayer?.let {
            if (musicPaused) {
                it.start()
                musicPaused = false
            }
        }
    }

    fun stopMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        currentMusicRes = 0
        musicPaused = false
    }

    fun performHapticIfEnabled(view: View, feedbackConstant: Int) {
        if (vibrationEnabled.value) {
            view.performHapticFeedback(feedbackConstant)
        }
    }

    fun release() {
        soundPool.release()
        stopMusic()
    }
}
