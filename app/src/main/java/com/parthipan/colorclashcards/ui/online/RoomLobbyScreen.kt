package com.parthipan.colorclashcards.ui.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.parthipan.colorclashcards.data.model.Room
import com.parthipan.colorclashcards.data.model.RoomPlayer
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomLobbyScreen(
    roomId: String,
    onBackClick: () -> Unit,
    onGameStart: (String, Boolean) -> Unit,
    viewModel: RoomLobbyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Start observing room
    LaunchedEffect(roomId) {
        viewModel.observeRoom(roomId)
    }

    // Handle game start
    LaunchedEffect(uiState.gameStarted) {
        if (uiState.gameStarted) {
            val isHost = uiState.isCurrentUserHost
            viewModel.clearGameStarted()
            onGameStart(roomId, isHost)
        }
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Leave room when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            // Don't leave if game started
            if (!uiState.gameStarted) {
                viewModel.leaveRoom()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room Lobby") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.leaveRoom()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBlue,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.room == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.room == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Room not found",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            val room = uiState.room!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Room code card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CardBlue.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Room Code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = room.roomCode,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = CardBlue
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(room.roomCode))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy Code",
                                    tint = CardBlue
                                )
                            }
                        }
                        Text(
                            text = "Share this code with friends to join!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Players section
                Text(
                    text = "Players (${room.players.size}/${room.maxPlayers})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(room.players) { player ->
                        PlayerCard(
                            player = player,
                            isCurrentUser = player.odId == uiState.currentUserId
                        )
                    }

                    // Empty slots
                    val emptySlots = room.maxPlayers - room.players.size
                    if (emptySlots > 0) {
                        items(emptySlots) {
                            EmptyPlayerSlot()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ready status info
                val readyCount = room.players.count { it.isReady }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (room.canStart()) {
                            CardGreen.copy(alpha = 0.2f)
                        } else {
                            CardYellow.copy(alpha = 0.2f)
                        }
                    )
                ) {
                    Text(
                        text = if (room.canStart()) {
                            "Ready to start! ($readyCount/${room.players.size} ready)"
                        } else {
                            "Waiting for players... ($readyCount/${room.players.size} ready)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ready/Not Ready button (not for host)
                    if (!uiState.isCurrentUserHost) {
                        OutlinedButton(
                            onClick = { viewModel.toggleReady() },
                            modifier = Modifier.weight(1f),
                            colors = if (uiState.isCurrentUserReady) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = CardGreen.copy(alpha = 0.2f)
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.isCurrentUserReady) "Ready!" else "Ready",
                                fontWeight = if (uiState.isCurrentUserReady) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Start button (host only)
                    if (uiState.isCurrentUserHost) {
                        Button(
                            onClick = { viewModel.startGame() },
                            modifier = Modifier.weight(1f),
                            enabled = room.canStart() && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = "Start Game",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Leave button
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.leaveRoom()
                        onBackClick()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CardRed
                    )
                ) {
                    Text("Leave Room")
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(
    player: RoomPlayer,
    isCurrentUser: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrentUser) {
                    Modifier.border(2.dp, CardBlue, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (player.isReady) {
                CardGreen.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            if (player.photoUrl != null) {
                AsyncImage(
                    model = player.photoUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(CardBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and status
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.odisplayName + if (isCurrentUser) " (You)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (player.isHost) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Host",
                            tint = CardYellow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = if (player.isHost) "Host" else if (player.isReady) "Ready" else "Not Ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        player.isHost -> CardYellow
                        player.isReady -> CardGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Ready indicator
            if (player.isReady) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(CardGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Ready",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlayerSlot() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Waiting for player...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
