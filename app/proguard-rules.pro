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

# Keep line numbers so crash reports have useful stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- LiteRT-LM ----------------------------------------------------------------
# LiteRT-LM uses JNI and internal reflection; stripping any class will crash
# inference at runtime. Keep the entire public API and all native methods.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- WorkManager --------------------------------------------------------------
# Worker subclasses are instantiated by WorkManager via reflection using the
# two-argument constructor. Stripping the constructor causes a ClassNotFoundException
# at runtime when the work is dequeued.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}