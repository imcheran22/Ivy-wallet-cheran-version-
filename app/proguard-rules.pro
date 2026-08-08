-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.servlet.**
-dontwarn org.apache.**
-dontwarn coil.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.**
-dontwarn org.slf4j.impl.**

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# --- Security hardening ---

# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
}

# Keep security classes intact (needed for runtime integrity checks)
-keep class com.ivy.wallet.security.** { *; }
-keep enum com.ivy.wallet.security.AppIntegrityChecker$InstallSource { *; }

# Obfuscate everything else aggressively
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Remove source file names and line numbers from stack traces in release
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Keep EncryptedSharedPreferences working
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
