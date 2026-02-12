# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Play In-App Update
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# Firebase Auth
-keepattributes Signature
-keepattributes *Annotation*

# Firestore
-keep class com.google.firebase.firestore.** { *; }

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep data classes for Firestore serialization
-keep class com.parthipan.colorclashcards.game.model.** { *; }
-keep class com.parthipan.colorclashcards.data.model.** { *; }

# Keep game engine classes
-keep class com.parthipan.colorclashcards.game.engine.** { *; }

# Keep UI classes
-keep class com.parthipan.colorclashcards.ui.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Strip debug/verbose logging in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
