# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }

# Optional compile-time annotation referenced by org.shredzone.commons.suncalc.
-dontwarn edu.umd.cs.findbugs.annotations.Nullable

# Strip debug/verbose logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Keep data classes for Room
-keepclassmembers class com.byd.wjx.data.local.entity.** { *; }
-keepclassmembers class com.byd.wjx.data.local.dao.** { *; }

# Guard the reflective app_process entry point for the binder daemon
-keep public class com.byd.wjx.helper.HelperDaemon { public static void main(java.lang.String[]); }
-keepclasseswithmembernames class * {
    native <methods>;
}
