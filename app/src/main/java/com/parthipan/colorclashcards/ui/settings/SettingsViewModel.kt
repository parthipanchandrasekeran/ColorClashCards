package com.parthipan.colorclashcards.ui.settings

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.parthipan.colorclashcards.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User profile state for settings screen.
 */
data class UserProfileState(
    val displayName: String = "",
    val photoUrl: String? = null,
    val isGuest: Boolean = false,
    val isSignedIn: Boolean = false
)

enum class UpdateStatus {
    Idle, Checking, Available, Downloading, Downloaded, UpToDate, Error
}

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

    private val _updateStatus = MutableStateFlow(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private var appUpdateManager: com.google.android.play.core.appupdate.AppUpdateManager? = null

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                _updateStatus.value = UpdateStatus.Downloading
            }
            InstallStatus.DOWNLOADED -> {
                _updateStatus.value = UpdateStatus.Downloaded
                appUpdateManager?.completeUpdate()
            }
            InstallStatus.FAILED -> {
                _updateStatus.value = UpdateStatus.Error
            }
            else -> {}
        }
    }

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
     * Check for app updates via Play Store In-App Updates API.
     */
    fun checkForUpdate(activity: Activity) {
        _updateStatus.value = UpdateStatus.Checking
        val manager = AppUpdateManagerFactory.create(activity)
        appUpdateManager = manager
        manager.registerListener(installStateListener)

        manager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                _updateStatus.value = UpdateStatus.Available
                // Prefer IMMEDIATE (handles install+restart automatically),
                // fall back to FLEXIBLE with completion listener
                val updateType = when {
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                    else -> null
                }
                if (updateType != null) {
                    try {
                        manager.startUpdateFlowForResult(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.defaultOptions(updateType),
                            REQUEST_CODE_UPDATE
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start update flow", e)
                        _updateStatus.value = UpdateStatus.Error
                    }
                } else {
                    Log.e(TAG, "No update type allowed")
                    _updateStatus.value = UpdateStatus.Error
                }
            } else {
                _updateStatus.value = UpdateStatus.UpToDate
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for updates", e)
            _updateStatus.value = UpdateStatus.Error
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

    override fun onCleared() {
        appUpdateManager?.unregisterListener(installStateListener)
        super.onCleared()
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val REQUEST_CODE_UPDATE = 1001
    }
}
