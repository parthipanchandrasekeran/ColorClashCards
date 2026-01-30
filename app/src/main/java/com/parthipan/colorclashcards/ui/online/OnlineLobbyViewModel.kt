package com.parthipan.colorclashcards.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.data.model.Room
import com.parthipan.colorclashcards.data.model.RoomStatus
import com.parthipan.colorclashcards.data.repository.RoomRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for online lobby entry.
 */
data class OnlineLobbyEntryState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val publicRooms: List<Room> = emptyList(),
    val createdRoom: Room? = null,
    val joinedRoom: Room? = null
)

/**
 * UI state for room lobby.
 */
data class RoomLobbyState(
    val room: Room? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCurrentUserHost: Boolean = false,
    val isCurrentUserReady: Boolean = false,
    val currentUserId: String = "",
    val gameStarted: Boolean = false
)

/**
 * ViewModel for online lobby entry screen.
 */
class OnlineLobbyEntryViewModel(
    private val roomRepository: RoomRepository = RoomRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineLobbyEntryState())
    val uiState: StateFlow<OnlineLobbyEntryState> = _uiState.asStateFlow()

    private var publicRoomsJob: Job? = null

    init {
        loadPublicRooms()
    }

    /**
     * Load public rooms.
     */
    private fun loadPublicRooms() {
        publicRoomsJob?.cancel()
        publicRoomsJob = viewModelScope.launch {
            roomRepository.observePublicRooms().collect { rooms ->
                _uiState.value = _uiState.value.copy(publicRooms = rooms)
            }
        }
    }

    /**
     * Create a new room.
     */
    fun createRoom(maxPlayers: Int = 4, isPublic: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = roomRepository.createRoom(maxPlayers, isPublic)

            result.fold(
                onSuccess = { room ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        createdRoom = room
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
     * Join a room by code.
     */
    fun joinRoomByCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = roomRepository.joinRoomByCode(code)

            result.fold(
                onSuccess = { room ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        joinedRoom = room
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
     * Join a public room.
     */
    fun joinPublicRoom(room: Room) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = roomRepository.joinRoomByCode(room.roomCode)

            result.fold(
                onSuccess = { joinedRoom ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        joinedRoom = joinedRoom.copy(id = room.id)
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
    fun clearNavigationState() {
        _uiState.value = _uiState.value.copy(
            createdRoom = null,
            joinedRoom = null
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
        publicRoomsJob?.cancel()
    }
}

/**
 * ViewModel for room lobby screen.
 */
class RoomLobbyViewModel(
    private val roomRepository: RoomRepository = RoomRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomLobbyState())
    val uiState: StateFlow<RoomLobbyState> = _uiState.asStateFlow()

    private var roomObserverJob: Job? = null
    private var currentRoomId: String? = null

    /**
     * Start observing a room.
     */
    fun observeRoom(roomId: String) {
        if (currentRoomId == roomId) return

        currentRoomId = roomId
        roomObserverJob?.cancel()

        val userId = roomRepository.currentUserId ?: ""
        _uiState.value = _uiState.value.copy(currentUserId = userId)

        roomObserverJob = viewModelScope.launch {
            roomRepository.observeRoom(roomId).collect { room ->
                if (room == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Room no longer exists"
                    )
                    return@collect
                }

                val isHost = room.hostId == userId
                val currentPlayer = room.players.find { it.odId == userId }
                val isReady = currentPlayer?.isReady ?: false
                val gameStarted = room.getStatus() == RoomStatus.PLAYING

                _uiState.value = _uiState.value.copy(
                    room = room,
                    isLoading = false,
                    isCurrentUserHost = isHost,
                    isCurrentUserReady = isReady,
                    gameStarted = gameStarted,
                    error = null
                )
            }
        }
    }

    /**
     * Toggle ready status.
     */
    fun toggleReady() {
        val roomId = currentRoomId ?: return
        val currentReady = _uiState.value.isCurrentUserReady

        viewModelScope.launch {
            val result = roomRepository.setPlayerReady(roomId, !currentReady)
            result.onFailure { e ->
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
        val roomId = currentRoomId ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = roomRepository.startGame(roomId)

            result.fold(
                onSuccess = {
                    // Game started - the observer will update gameStarted flag
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to start game"
                    )
                }
            )
        }
    }

    /**
     * Leave the room.
     */
    fun leaveRoom() {
        val roomId = currentRoomId ?: return

        viewModelScope.launch {
            roomRepository.leaveRoom(roomId)
        }
    }

    /**
     * Clear error.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clear game started flag after navigating.
     */
    fun clearGameStarted() {
        _uiState.value = _uiState.value.copy(gameStarted = false)
    }

    override fun onCleared() {
        super.onCleared()
        roomObserverJob?.cancel()
    }
}
