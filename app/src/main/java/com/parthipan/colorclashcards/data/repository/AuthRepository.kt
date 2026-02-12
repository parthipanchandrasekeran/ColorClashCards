package com.parthipan.colorclashcards.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.parthipan.colorclashcards.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for authentication operations.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /**
     * Get current Firebase user.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Check if user is signed in.
     */
    val isSignedIn: Boolean
        get() = auth.currentUser != null

    /**
     * Observe auth state changes.
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Sign in with Google credential (ID token).
     */
    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Sign in failed"))

            val user = User(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "Player",
                photoUrl = firebaseUser.photoUrl?.toString(),
                isGuest = false,
                createdAt = Timestamp.now()
            )

            // Save user to Firestore
            saveUserToFirestore(user)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign in anonymously (Guest mode).
     */
    suspend fun signInAnonymously(): Result<User> {
        return try {
            val authResult = auth.signInAnonymously().await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Anonymous sign in failed"))

            val user = User(
                uid = firebaseUser.uid,
                displayName = "Guest",
                photoUrl = null,
                isGuest = true,
                createdAt = Timestamp.now()
            )

            // Save user to Firestore
            saveUserToFirestore(user)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out current user.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Get user profile from Firestore.
     */
    suspend fun getUserProfile(uid: String): Result<User?> {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            if (document.exists()) {
                val user = document.data?.let { User.fromMap(uid, it) }
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save user profile to Firestore.
     */
    private suspend fun saveUserToFirestore(user: User) {
        try {
            // Check if user already exists
            val existingDoc = firestore.collection("users").document(user.uid).get().await()
            if (!existingDoc.exists()) {
                // Only create if doesn't exist (preserve createdAt)
                firestore.collection("users")
                    .document(user.uid)
                    .set(user.toMap())
                    .await()
            } else {
                // Update displayName and photoUrl only
                firestore.collection("users")
                    .document(user.uid)
                    .update(
                        mapOf(
                            "displayName" to user.displayName,
                            "photoUrl" to user.photoUrl
                        )
                    )
                    .await()
            }
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to save user to Firestore", e)
        }
    }
}
