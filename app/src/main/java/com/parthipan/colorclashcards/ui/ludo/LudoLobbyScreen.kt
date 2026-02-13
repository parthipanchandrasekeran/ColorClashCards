package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.data.model.LudoRoom
import com.parthipan.colorclashcards.data.model.LudoRoomPlayer
import com.parthipan.colorclashcards.game.ludo.model.LudoColor

/**
 * Ludo lobby entry screen - create or join rooms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoLobbyEntryScreen(
    onBackClick: () -> Unit,
    onRoomCreated: (String) -> Unit,
    onRoomJoined: (String) -> Unit,
    viewModel: LudoLobbyEntryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var roomCode by remember { mutableStateOf("") }

    // Handle navigation
    LaunchedEffect(uiState.createdRoomId) {
        uiState.createdRoomId?.let {
            onRoomCreated(it)
            viewModel.clearNavigation()
        }
    }

    LaunchedEffect(uiState.joinedRoomId) {
        uiState.joinedRoomId?.let {
            onRoomJoined(it)
            viewModel.clearNavigation()
        }
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ludo Online", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LudoBoardColors.Green,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .testTag("ludoLobbyScreen")
        ) {
            // Join by code section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Join by Room Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = roomCode,
                            onValueChange = { if (it.length <= 6) roomCode = it.uppercase() },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Enter 6-digit code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.joinRoomByCode(roomCode) }
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.joinRoomByCode(roomCode) },
                            enabled = roomCode.length == 6 && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LudoBoardColors.Green
                            )
                        ) {
                            Text("Join")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Create room button
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LudoBoardColors.Green
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Room", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Public rooms list
            Text(
                text = "Public Rooms",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.publicRooms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No public rooms available.\nCreate one to start playing!",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.publicRooms) { room ->
                        PublicRoomCard(
                            room = room,
                            onJoin = { viewModel.joinRoom(room.id) }
                        )
                    }
                }
            }
        }

        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Create room dialog
    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { maxPlayers, isPublic ->
                showCreateDialog = false
                viewModel.createRoom(maxPlayers, isPublic)
            }
        )
    }
}

/**
 * Public room card.
 */
@Composable
private fun PublicRoomCard(
    room: LudoRoom,
    onJoin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJoin),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${room.hostName}'s Room",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Code: ${room.roomCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Player count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${room.players.size}/${room.maxPlayers}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onJoin,
                enabled = !room.isFull(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LudoBoardColors.Green
                )
            ) {
                Text("Join")
            }
        }
    }
}

/**
 * Create room dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (maxPlayers: Int, isPublic: Boolean) -> Unit
) {
    var maxPlayers by remember { mutableIntStateOf(4) }
    var isPublic by remember { mutableStateOf(true) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Room", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Max Players",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(2, 3, 4).forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = maxPlayers == count,
                            onClick = { maxPlayers = count },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                        ) {
                            Text("$count")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Public Room")
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it }
                    )
                }

                Text(
                    text = if (isPublic) "Anyone can join" else "Share code to invite",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(maxPlayers, isPublic) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LudoBoardColors.Green
                )
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Ludo room lobby screen - waiting room before game starts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoRoomLobbyScreen(
    roomId: String,
    onBackClick: () -> Unit,
    onGameStart: (roomId: String, isHost: Boolean) -> Unit,
    viewModel: LudoRoomLobbyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    // Initialize
    LaunchedEffect(roomId) {
        viewModel.initialize(roomId)
    }

    // Handle game start
    LaunchedEffect(uiState.gameStarted) {
        if (uiState.gameStarted) {
            onGameStart(roomId, uiState.isHost)
        }
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val room = uiState.room

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Lobby", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.leaveRoom()
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Leave")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LudoBoardColors.Green,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading || room == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Room code card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = LudoBoardColors.Green.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Room Code",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = room.roomCode,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(room.roomCode))
                            }) {
                                Icon(Icons.Default.Share, "Copy code")
                            }
                        }

                        Text(
                            text = "Share this code with friends to join!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Players list
                Text(
                    text = "Players (${room.players.size}/${room.maxPlayers})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val usedColors = room.players.map { it.color }.toSet()

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    room.players.forEach { player ->
                        val isLocalPlayer = player.odId == currentUserId
                        LobbyPlayerCard(
                            player = player,
                            isLocalPlayer = isLocalPlayer,
                            usedColors = usedColors,
                            colorChangeInProgress = uiState.colorChangeInProgress,
                            onColorSelected = if (isLocalPlayer) {
                                { color -> viewModel.changeColor(color) }
                            } else null
                        )
                    }

                    // Empty slots
                    repeat(room.maxPlayers - room.players.size) {
                        EmptyPlayerSlot()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ready / Start button
                if (uiState.isHost) {
                    Button(
                        onClick = { viewModel.startGame() },
                        enabled = uiState.canStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LudoBoardColors.Green
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (uiState.canStart) "Start Game" else "Waiting for players...",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.setReady(!uiState.isReady) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isReady) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                LudoBoardColors.Green
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isReady) {
                            Icon(Icons.Default.Check, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ready!", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Ready Up", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Player card in lobby.
 */
@Composable
private fun LobbyPlayerCard(
    player: LudoRoomPlayer,
    isLocalPlayer: Boolean = false,
    usedColors: Set<String> = emptySet(),
    colorChangeInProgress: Boolean = false,
    onColorSelected: ((LudoColor) -> Unit)? = null
) {
    val playerColor = try {
        LudoBoardColors.getColor(LudoColor.valueOf(player.color))
    } catch (e: Exception) {
        LudoBoardColors.Red
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = playerColor.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(playerColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = player.odisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (player.isHost) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HOST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = player.color,
                        style = MaterialTheme.typography.bodySmall,
                        color = playerColor
                    )
                }

                // Ready indicator
                if (player.isReady) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(LudoBoardColors.FinishedBadge),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Color picker for local player
            if (isLocalPlayer && onColorSelected != null) {
                LobbyColorPicker(
                    selectedColor = player.color,
                    usedColors = usedColors,
                    enabled = !colorChangeInProgress,
                    onColorSelected = onColorSelected,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }
    }
}

/**
 * Color picker row for lobby — shows 4 color circles.
 */
@Composable
private fun LobbyColorPicker(
    selectedColor: String,
    usedColors: Set<String>,
    enabled: Boolean,
    onColorSelected: (LudoColor) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LudoColor.entries.forEach { color ->
            val isSelected = color.name == selectedColor
            val isTakenByOther = color.name in usedColors && !isSelected
            val displayColor = LudoBoardColors.getColor(color)

            val targetSize = if (isSelected) 36f else 28f
            val animatedSize by animateFloatAsState(
                targetValue = targetSize,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "colorSize"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(animatedSize.dp)
                        .then(
                            if (isSelected) Modifier.shadow(4.dp, CircleShape)
                            else Modifier
                        )
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier
                        )
                        .clip(CircleShape)
                        .background(displayColor.copy(alpha = if (isTakenByOther) 0.25f else 1f))
                        .then(
                            if (!isTakenByOther && !isSelected && enabled) {
                                Modifier.clickable { onColorSelected(color) }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isSelected -> Icon(
                            Icons.Default.Check,
                            contentDescription = "${color.name} selected",
                            tint = Color.White,
                            modifier = Modifier.size((animatedSize * 0.55f).dp)
                        )
                        isTakenByOther -> Icon(
                            Icons.Default.Close,
                            contentDescription = "${color.name} taken",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size((animatedSize * 0.55f).dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = color.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTakenByOther) {
                        displayColor.copy(alpha = 0.4f)
                    } else {
                        displayColor
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Empty player slot.
 */
@Composable
private fun EmptyPlayerSlot() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Gray.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = Color.Gray.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Waiting for player...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

