package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp

/**
 * User data model stored in Firestore.
 */
data class User(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val isGuest: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
) {
    /**
     * Convert to Map for Firestore.
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "displayName" to displayName,
        "photoUrl" to photoUrl,
        "isGuest" to isGuest,
        "createdAt" to createdAt
    )

    companion object {
        /**
         * Create User from Firestore document.
         */
        fun fromMap(uid: String, map: Map<String, Any?>): User {
            return User(
                uid = uid,
                displayName = map["displayName"] as? String ?: "",
                photoUrl = map["photoUrl"] as? String,
                isGuest = map["isGuest"] as? Boolean ?: false,
                createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
            )
        }
    }
}
