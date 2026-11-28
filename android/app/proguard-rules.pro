# Spendwise ProGuard / R8 Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip debug/info/verbose Android logs in release APK
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlinx Serialization (Supabase & Edge Function models)
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class com.spendwise.app.data.model.**$$serializer { *; }
-keepclassmembers class com.spendwise.app.data.model.** {
    *** Companion;
}
-keep class com.spendwise.app.data.model.** { *; }
-keep class com.spendwise.app.data.remote.AppUpdateInfo** { *; }
-keep class com.spendwise.app.voice.** { *; }
-keep,includedescriptorclasses class com.spendwise.app.data.remote.AppUpdateInfo$$serializer { *; }
-keep,includedescriptorclasses class com.spendwise.app.voice.**$$serializer { *; }

# Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Ktor & Supabase-kt networking internals
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**
-dontwarn kotlinx.atomicfu.**
-dontwarn org.slf4j.**
-keep class io.ktor.** { *; }
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.ktor.** { *; }

# Google Credential Manager & Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Coroutines & Serialization runtime
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
