# ─── Lumera ProGuard / R8 Rules ───

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*

# ─── App code ───
# Gson reads and writes app classes by field name and instantiates them reflectively,
# so field names and constructors stay. Everything else (methods, unused classes,
# class merging, inlining) is left to R8. Room, Hilt and Retrofit ship their own rules.
-keepclassmembers class com.hereliesaz.illumera.** {
    <fields>;
    <init>(...);
}
-keepclassmembers enum com.hereliesaz.illumera.** { *; }

# ─── Gson ───
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ─── Room ───
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class *_Impl { *; }

# ─── Hilt / Dagger ───
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# NanoHTTPD, ZXing, AndroidX Security, Media3, Retrofit and OkHttp are left to
# their own consumer rules: none of them is reached by reflection from app code.

# ─── Compose ───
-dontwarn androidx.compose.**

# ─── Media3 / ExoPlayer ───
-dontwarn androidx.media3.**

# ─── Retrofit ───
# Keep generic signature and annotations for Retrofit + Gson
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Kotlin coroutines continuation (needed by Retrofit suspend functions)
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Kotlin Metadata (needed by Retrofit to understand suspend functions)
-keep class kotlin.Metadata { *; }

# ─── OkHttp ───
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── ACRA (Crash Reporting) ───
-keep class org.acra.** { *; }
-dontwarn org.acra.**

# ─── General ───
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
