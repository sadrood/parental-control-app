# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson serialized classes
-keep class com.example.parentalcontrol.network.model.** { *; }
-keep class com.example.parentalcontrol.data.entity.** { *; }

# Keep Socket.IO
-keep class io.socket.** { *; }
-keep class org.json.** { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
