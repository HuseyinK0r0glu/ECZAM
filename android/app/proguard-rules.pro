# Flutter local notifications serializes notification details with Gson;
# R8 must not strip these or scheduling crashes at runtime.
-keep class com.dexterous.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses, EnclosingMethod

# Gson generic type tokens rely on reflection.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Flutter embedding (R8 in release with deferred components).
-keep class io.flutter.embedding.** { *; }

# The Flutter embedding references Play Core (split install / deferred
# components) which this app does not use. Without these, R8 fails with
# "Missing class com.google.android.play.core.*".
-dontwarn com.google.android.play.core.**
-keep class com.google.android.play.core.** { *; }
