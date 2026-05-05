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

# Conscrypt — bundled to provide AES/GCM-SIV/NoPadding on Android API ≤ 29 where
# the system Conscrypt lacks it. The provider registers algorithms via reflection
# on Service class names, and JNI binds native methods by name, so both must
# survive R8 minification.
-keep class org.conscrypt.** { *; }
-keep interface org.conscrypt.** { *; }
-keepclasseswithmembernames class org.conscrypt.** {
    native <methods>;
}
-dontwarn org.conscrypt.**