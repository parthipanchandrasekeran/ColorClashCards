package com.parthipan.colorclashcards.ui.settings

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.play.core.appupdate.AppUpdateInfo
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
    private var isCheckInProgress = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                _updateStatus.value = UpdateStatus.Downloading
            }
            InstallStatus.DOWNLOADED -> {
                _updateStatus.value = UpdateStatus.Downloaded
                appUpdateManager?.completeUpdate()
            }
            InstallStatus.INSTALLED -> {
                _updateStatus.value = UpdateStatus.Idle
                cleanupUpdateManager()
            }
            InstallStatus.FAILED -> {
                _updateStatus.value = UpdateStatus.Error
                isCheckInProgress = false
            }
            InstallStatus.CANCELED -> {
                _updateStatus.value = UpdateStatus.Idle
                isCheckInProgress = false
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
     * Protected against rapid multiple clicks via isCheckInProgress flag.
     */
    fun checkForUpdate(activity: Activity) {
        if (isCheckInProgress) return
        isCheckInProgress = true

        _updateStatus.value = UpdateStatus.Checking

        // Unregister previous listener before creating new manager
        cleanupUpdateManager()

        val manager = AppUpdateManagerFactory.create(activity)
        appUpdateManager = manager
        manager.registerListener(installStateListener)

        manager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                _updateStatus.value = UpdateStatus.Available
                startUpdateFlow(manager, appUpdateInfo, activity)
            } else {
                _updateStatus.value = UpdateStatus.UpToDate
                isCheckInProgress = false
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for updates", e)
            _updateStatus.value = UpdateStatus.Error
            isCheckInProgress = false
        }
    }

    /**
     * Start the update flow. Prefers IMMEDIATE, falls back to FLEXIBLE.
     */
    private fun startUpdateFlow(
        manager: com.google.android.play.core.appupdate.AppUpdateManager,
        appUpdateInfo: AppUpdateInfo,
        activity: Activity
    ) {
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
                // For IMMEDIATE updates, the Play Store handles everything.
                // For FLEXIBLE, the installStateListener will track progress.
                if (updateType == AppUpdateType.IMMEDIATE) {
                    // IMMEDIATE flow takes over the screen — status will resolve
                    // when the app restarts or the user cancels
                    _updateStatus.value = UpdateStatus.Downloading
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start update flow", e)
                _updateStatus.value = UpdateStatus.Error
                isCheckInProgress = false
            }
        } else {
            Log.e(TAG, "No update type allowed")
            _updateStatus.value = UpdateStatus.Error
            isCheckInProgress = false
        }
    }

    /**
     * Handle the result from the update flow activity.
     * Call this from the Activity's onActivityResult.
     */
    fun onUpdateResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                // Update accepted — for FLEXIBLE, listener handles the rest
                // For IMMEDIATE, the app will restart
            }
            Activity.RESULT_CANCELED -> {
                _updateStatus.value = UpdateStatus.Idle
                isCheckInProgress = false
            }
            else -> {
                _updateStatus.value = UpdateStatus.Error
                isCheckInProgress = false
            }
        }
    }

    /**
     * Clean up the update manager and unregister listener.
     */
    private fun cleanupUpdateManager() {
        appUpdateManager?.unregisterListener(installStateListener)
        appUpdateManager = null
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
        cleanupUpdateManager()
        super.onCleared()
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        const val REQUEST_CODE_UPDATE = 1001
    }
}
