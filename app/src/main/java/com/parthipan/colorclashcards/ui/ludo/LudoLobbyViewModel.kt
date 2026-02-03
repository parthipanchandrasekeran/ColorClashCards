package com.parthipan.colorclashcards.ui.ludo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.data.model.LudoRoom
import com.parthipan.colorclashcards.data.model.LudoRoomStatus
import com.parthipan.colorclashcards.data.repository.LudoRoomRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Ludo lobby entry screen.
 */
data class LudoLobbyEntryUiState(
    val isLoading: Boolean = false,
    val publicRooms: List<LudoRoom> = emptyList(),
    val error: String? = null,
    val createdRoomId: String? = null,
    val joinedRoomId: String? = null
)

/**
 * UI State for Ludo room lobby.
 */
data class LudoRoomLobbyUiState(
    val room: LudoRoom? = null,
    val isLoading: Boolean = true,
    val isHost: Boolean = false,
    val isReady: Boolean = false,
    val canStart: Boolean = false,
    val error: String? = null,
    val gameStarted: Boolean = false
)

/**
 * ViewModel for Ludo lobby entry (create/join).
 */
class LudoLobbyEntryViewModel : ViewModel() {

    private val repository = LudoRoomRepository()

    private val _uiState = MutableStateFlow(LudoLobbyEntryUiState())
    val uiState: StateFlow<LudoLobbyEntryUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observePublicRooms()
        checkActiveRoom()
    }

    /**
     * Check for active room to rejoin.
     */
    private fun checkActiveRoom() {
        viewModelScope.launch {
            val result = repository.getActiveRoom()
            result.getOrNull()?.let { activeRoom ->
                _uiState.value = _uiState.value.copy(
                    joinedRoomId = activeRoom.roomId
                )
            }
        }
    }

    /**
     * Observe public rooms.
     */
    private fun observePublicRooms() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observePublicRooms().collect { rooms ->
                _uiState.value = _uiState.value.copy(publicRooms = rooms)
            }
        }
    }

    /**
     * Create a new room.
     */
    fun createRoom(maxPlayers: Int, isPublic: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.createRoom(maxPlayers, isPublic).fold(
                onSuccess = { room ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        createdRoomId = room.id
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create room"
                    )
                }
            )
        }
    }

    /**
     * Join room by code.
     */
    fun joinRoomByCode(code: String) {
        if (code.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Room code must be 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.joinRoomByCode(code).fold(
                onSuccess = { room ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        joinedRoomId = room.id
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to join room"
                    )
                }
            )
        }
    }

    /**
     * Join room directly.
     */
    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.joinRoomById(roomId).fold(
                onSuccess = { room ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        joinedRoomId = room.id
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to join room"
                    )
                }
            )
        }
    }

    /**
     * Clear navigation state after navigating.
     */
    fun clearNavigation() {
        _uiState.value = _uiState.value.copy(
            createdRoomId = null,
            joinedRoomId = null
        )
    }

    /**
     * Clear error.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }
}

/**
 * ViewModel for Ludo room lobby (waiting room).
 */
class LudoRoomLobbyViewModel : ViewModel() {

    private val repository = LudoRoomRepository()

    private val _uiState = MutableStateFlow(LudoRoomLobbyUiState())
    val uiState: StateFlow<LudoRoomLobbyUiState> = _uiState.asStateFlow()

    private var roomId: String = ""
    private var observeJob: Job? = null

    /**
     * Initialize with room ID.
     */
    fun initialize(roomId: String) {
        this.roomId = roomId
        observeRoom()
    }

    /**
     * Observe room for real-time updates.
     */
    private fun observeRoom() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeRoom(roomId).collect { room ->
                if (room == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Room not found"
                    )
                    return@collect
                }

                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val isHost = room.hostId == currentUserId
                val currentPlayer = room.players.find { it.odId == currentUserId }
                val allReady = room.players.all { it.isReady }
                val canStart = isHost && allReady && room.players.size >= 2

                _uiState.value = _uiState.value.copy(
                    room = room,
                    isLoading = false,
                    isHost = isHost,
                    isReady = currentPlayer?.isReady ?: false,
                    canStart = canStart,
                    gameStarted = room.status == LudoRoomStatus.PLAYING.name
                )
            }
        }
    }

    /**
     * Set ready status.
     */
    fun setReady(isReady: Boolean) {
        viewModelScope.launch {
            repository.setReady(roomId, isReady).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update ready status"
                )
            }
        }
    }

    /**
     * Start the game (host only).
     */
    fun startGame() {
        viewModelScope.launch {
            repository.startGame(roomId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to start game"
                )
            }
        }
    }

    /**
     * Leave the room.
     */
    fun leaveRoom() {
        viewModelScope.launch {
            repository.leaveRoom(roomId)
        }
    }

    /**
     * Clear error.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }
}
