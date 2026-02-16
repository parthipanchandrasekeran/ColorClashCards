package com.parthipan.colorclashcards.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.parthipan.colorclashcards.audio.LocalSoundManager
import com.parthipan.colorclashcards.voice.VoiceChatManager
import com.parthipan.colorclashcards.voice.VoiceChatStatus

/**
 * Self-contained voice chat controls: mic FAB + participant indicators.
 * Handles permission request, join/leave lifecycle, and mute toggle.
 *
 * - Tap: join (if disconnected, requests permission) or toggle mute (if connected)
 * - Long-press: leave voice channel
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceChatControls(
    voiceChatManager: VoiceChatManager,
    roomPath: String,
    displayName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = LocalSoundManager.current
    val state by voiceChatManager.uiState.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            soundManager.duckMusicForVoiceChat()
            voiceChatManager.joinVoiceChat(roomPath, displayName)
        }
    }

    // Auto-leave and restore music on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (state.status != VoiceChatStatus.DISCONNECTED) {
                voiceChatManager.leaveVoiceChat()
                soundManager.restoreMusicVolume()
            }
        }
    }

    // Duck/restore music based on voice chat status
    DisposableEffect(state.status) {
        if (state.status == VoiceChatStatus.CONNECTED || state.status == VoiceChatStatus.CONNECTING) {
            soundManager.duckMusicForVoiceChat()
        } else {
            soundManager.restoreMusicVolume()
        }
        onDispose {}
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // Participant indicators (above the FAB)
        if (state.status != VoiceChatStatus.DISCONNECTED && state.participants.size > 1) {
            VoiceParticipantIndicators(
                participants = state.participants,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Mic FAB
        VoiceMicFab(
            status = state.status,
            isMuted = state.isMuted,
            onClick = {
                when (state.status) {
                    VoiceChatStatus.DISCONNECTED -> {
                        // Check / request permission then join
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            soundManager.duckMusicForVoiceChat()
                            voiceChatManager.joinVoiceChat(roomPath, displayName)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    VoiceChatStatus.CONNECTING,
                    VoiceChatStatus.CONNECTED -> {
                        voiceChatManager.toggleMute()
                    }
                }
            },
            onLongClick = {
                if (state.status != VoiceChatStatus.DISCONNECTED) {
                    voiceChatManager.leaveVoiceChat()
                    soundManager.restoreMusicVolume()
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceMicFab(
    status: VoiceChatStatus,
    isMuted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Pulsing animation when connected and not muted
    val isActive = status == VoiceChatStatus.CONNECTED && !isMuted
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            status == VoiceChatStatus.DISCONNECTED -> Color(0xFF666666)
            isMuted -> Color(0xFFD32F2F)
            else -> Color(0xFF4CAF50)
        },
        label = "fabColor"
    )

    FloatingActionButton(
        onClick = { /* handled by combinedClickable */ },
        modifier = Modifier
            .scale(pulseScale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        containerColor = containerColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp
        ),
        shape = CircleShape
    ) {
        Icon(
            imageVector = when {
                status == VoiceChatStatus.DISCONNECTED -> Icons.Default.MicOff
                isMuted -> Icons.Default.MicOff
                else -> Icons.Default.Mic
            },
            contentDescription = when {
                status == VoiceChatStatus.DISCONNECTED -> "Join voice chat"
                isMuted -> "Unmute"
                else -> "Mute"
            },
            tint = Color.White
        )
    }
}

@Composable
private fun VoiceParticipantIndicators(
    participants: Map<String, com.parthipan.colorclashcards.voice.VoiceChatParticipant>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-6).dp)
    ) {
        participants.entries.take(4).forEach { (_, participant) ->
            val initials = remember(participant.displayName) {
                participant.displayName
                    .split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .joinToString("")
                    .ifEmpty { "?" }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (participant.isMuted) Color(0xFF777777) else Color(0xFF4CAF50)
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Mute overlay
                if (participant.isMuted) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                    )
                }
            }
        }
    }
}
