# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes used for SharedPreferences/JSON
-keep class com.ianocent.musicplayer.data.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Palette
-keep class androidx.palette.** { *; }

# Accompanist
-keep class com.google.accompanist.** { *; }
