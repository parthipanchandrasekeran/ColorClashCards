package com.parthipan.colorclashcards.ui.snl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.data.model.SnlRoom
import com.parthipan.colorclashcards.data.model.SnlRoomStatus
import com.parthipan.colorclashcards.data.repository.SnlRoomRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SnlLobbyEntryUiState(
    val isLoading: Boolean = false,
    val publicRooms: List<SnlRoom> = emptyList(),
    val error: String? = null,
    val createdRoomId: String? = null,
    val joinedRoomId: String? = null
)

data class SnlRoomLobbyUiState(
    val room: SnlRoom? = null,
    val isLoading: Boolean = true,
    val isHost: Boolean = false,
    val isReady: Boolean = false,
    val canStart: Boolean = false,
    val error: String? = null,
    val gameStarted: Boolean = false,
    val colorChangeInProgress: Boolean = false
)

class SnlLobbyEntryViewModel : ViewModel() {

    private val repository = SnlRoomRepository()
    private val _uiState = MutableStateFlow(SnlLobbyEntryUiState())
    val uiState: StateFlow<SnlLobbyEntryUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init { observePublicRooms() }

    private fun observePublicRooms() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observePublicRooms().collect { rooms ->
                _uiState.value = _uiState.value.copy(publicRooms = rooms)
            }
        }
    }

    fun createRoom(maxPlayers: Int, isPublic: Boolean, boardLayout: String = "CLASSIC") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.createRoom(maxPlayers, isPublic, boardLayout).fold(
                onSuccess = { room -> _uiState.value = _uiState.value.copy(isLoading = false, createdRoomId = room.id) },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to create room") }
            )
        }
    }

    fun joinRoomByCode(code: String) {
        if (code.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Room code must be 6 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.joinRoomByCode(code).fold(
                onSuccess = { room -> _uiState.value = _uiState.value.copy(isLoading = false, joinedRoomId = room.id) },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to join room") }
            )
        }
    }

    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.joinRoomById(roomId).fold(
                onSuccess = { room -> _uiState.value = _uiState.value.copy(isLoading = false, joinedRoomId = room.id) },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to join room") }
            )
        }
    }

    fun clearNavigation() { _uiState.value = _uiState.value.copy(createdRoomId = null, joinedRoomId = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() { super.onCleared(); observeJob?.cancel() }
}

class SnlRoomLobbyViewModel : ViewModel() {

    private val repository = SnlRoomRepository()
    private val _uiState = MutableStateFlow(SnlRoomLobbyUiState())
    val uiState: StateFlow<SnlRoomLobbyUiState> = _uiState.asStateFlow()
    private var roomId: String = ""
    private var observeJob: Job? = null

    fun initialize(roomId: String) {
        this.roomId = roomId
        observeRoom()
    }

    private fun observeRoom() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeRoom(roomId).collect { room ->
                if (room == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Room not found")
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
                    gameStarted = room.status == SnlRoomStatus.PLAYING.name
                )
            }
        }
    }

    fun setReady(isReady: Boolean) {
        viewModelScope.launch {
            repository.setReady(roomId, isReady).onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update ready status")
            }
        }
    }

    fun changeColor(colorIndex: Int) {
        if (_uiState.value.colorChangeInProgress) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(colorChangeInProgress = true)
            repository.changePlayerColor(roomId, colorIndex).onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to change color")
            }
            _uiState.value = _uiState.value.copy(colorChangeInProgress = false)
        }
    }

    fun startGame() {
        viewModelScope.launch {
            repository.startGame(roomId).onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to start game")
            }
        }
    }

    fun leaveRoom() { viewModelScope.launch { repository.leaveRoom(roomId) } }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() { super.onCleared(); observeJob?.cancel() }
}
