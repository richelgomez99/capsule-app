# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Orbit 002: Room entities ---
-keep class com.capsule.app.data.entity.** { *; }
-keep class com.capsule.app.data.model.** { *; }

# --- Orbit 002: Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.capsule.app.**$$serializer { *; }
-keepclassmembers class com.capsule.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.capsule.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Orbit 002: AIDL Parcelables ---
-keep class com.capsule.app.data.ipc.** { *; }
-keep class com.capsule.app.net.ipc.** { *; }