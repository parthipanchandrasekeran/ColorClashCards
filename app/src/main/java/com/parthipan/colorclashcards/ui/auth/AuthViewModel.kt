package com.parthipan.colorclashcards.ui.auth

import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.parthipan.colorclashcards.data.model.User
import com.parthipan.colorclashcards.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authentication UI state.
 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * ViewModel for authentication screen.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Web Client ID from Firebase Console
    companion object {
        const val WEB_CLIENT_ID = "7833973970-lfmejp264dnsbcj1eo4tmquofkci94hc.apps.googleusercontent.com"
    }

    /**
     * Check if user is already signed in.
     */
    fun checkAuthState() {
        if (authRepository.isSignedIn) {
            val currentUser = authRepository.currentUser
            if (currentUser != null) {
                _uiState.value = AuthUiState.Success(
                    User(
                        uid = currentUser.uid,
                        displayName = currentUser.displayName ?: "Player",
                        photoUrl = currentUser.photoUrl?.toString(),
                        isGuest = currentUser.isAnonymous
                    )
                )
            }
        }
    }

    /**
     * Get Google Sign-In options.
     */
    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }

    /**
     * Set loading state when starting Google Sign-In.
     */
    fun setLoading() {
        _uiState.value = AuthUiState.Loading
    }

    /**
     * Handle the result from Google Sign-In activity.
     */
    fun handleGoogleSignInResult(result: ActivityResult) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                if (idToken != null) {
                    val authResult = authRepository.signInWithGoogle(idToken)
                    authResult.fold(
                        onSuccess = { user ->
                            _uiState.value = AuthUiState.Success(user)
                        },
                        onFailure = { error ->
                            _uiState.value = AuthUiState.Error(
                                error.message ?: "Firebase sign-in failed"
                            )
                        }
                    )
                } else {
                    _uiState.value = AuthUiState.Error("Sign-in failed. Please try again.")
                }
            } catch (e: ApiException) {
                _uiState.value = AuthUiState.Error(
                    "Sign-in failed. Please try again."
                )
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(
                    e.message ?: "An unexpected error occurred. Please try again."
                )
            }
        }
    }

    /**
     * Continue as guest (anonymous authentication).
     */
    fun continueAsGuest() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val result = authRepository.signInAnonymously()

            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(
                        error.message ?: "Guest sign-in failed"
                    )
                }
            )
        }
    }

    /**
     * Reset UI state (e.g., after showing error).
     */
    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
