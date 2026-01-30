package com.parthipan.colorclashcards.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.data.repository.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Active game info for reconnection.
 */
data class ActiveGameInfo(
    val roomId: String,
    val isHost: Boolean
)

/**
 * UI state for home screen.
 */
data class HomeUiState(
    val activeGame: ActiveGameInfo? = null,
    val isCheckingActiveGame: Boolean = true
)

/**
 * ViewModel for HomeScreen.
 * Handles checking for active games to allow reconnection.
 */
class HomeViewModel(
    private val matchRepository: MatchRepository = MatchRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        checkForActiveGame()
    }

    /**
     * Check if user has an active game to reconnect to.
     */
    fun checkForActiveGame() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingActiveGame = true)

            try {
                val result = matchRepository.getActiveRoom()
                val activeRoomInfo = result.getOrNull()

                if (activeRoomInfo != null) {
                    _uiState.value = _uiState.value.copy(
                        activeGame = ActiveGameInfo(
                            roomId = activeRoomInfo.roomId,
                            isHost = activeRoomInfo.isHost
                        ),
                        isCheckingActiveGame = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        activeGame = null,
                        isCheckingActiveGame = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    activeGame = null,
                    isCheckingActiveGame = false
                )
            }
        }
    }

    /**
     * Clear active game (used after game ends).
     */
    fun clearActiveGame() {
        _uiState.value = _uiState.value.copy(activeGame = null)
    }
}
