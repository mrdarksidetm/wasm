# ProGuard / R8 rules for Wasm (Production Release)

# AndroidX Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Data Models & Entities
-keep class com.mrdartsidetm.wasm.data.** { *; }

# Jetpack Compose
-keep class androidx.compose.material.icons.** { *; }
-dontwarn androidx.compose.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Jetpack DataStore Preferences
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
