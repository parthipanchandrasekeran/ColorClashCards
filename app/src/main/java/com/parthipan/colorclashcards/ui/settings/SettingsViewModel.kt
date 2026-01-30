package com.parthipan.colorclashcards.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.parthipan.colorclashcards.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * User profile state for settings screen.
 */
data class UserProfileState(
    val displayName: String = "",
    val photoUrl: String? = null,
    val isGuest: Boolean = false,
    val isSignedIn: Boolean = false
)

/**
 * ViewModel for settings screen.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _userProfile = MutableStateFlow(UserProfileState())
    val userProfile: StateFlow<UserProfileState> = _userProfile.asStateFlow()

    private val _signOutComplete = MutableStateFlow(false)
    val signOutComplete: StateFlow<Boolean> = _signOutComplete.asStateFlow()

    init {
        loadUserProfile()
    }

    /**
     * Load current user profile.
     */
    private fun loadUserProfile() {
        val currentUser = authRepository.currentUser
        if (currentUser != null) {
            _userProfile.value = UserProfileState(
                displayName = currentUser.displayName ?: "Guest",
                photoUrl = currentUser.photoUrl?.toString(),
                isGuest = currentUser.isAnonymous,
                isSignedIn = true
            )
        }
    }

    /**
     * Sign out current user.
     */
    fun signOut() {
        authRepository.signOut()
        _signOutComplete.value = true
    }

    /**
     * Reset sign out state.
     */
    fun resetSignOutState() {
        _signOutComplete.value = false
    }
}
