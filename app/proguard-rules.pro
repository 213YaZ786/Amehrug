# Project rules. Compose and AndroidX ship their own consumer rules.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# SQLCipher: the Java classes are reached from JNI.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Bouncy Castle: only the lightweight Argon2 API is used, no JCE provider and
# no reflection, so nothing needs keeping. These classes reference optional
# platform pieces that Android does not have.
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn org.bouncycastle.jce.provider.**
-dontwarn org.bouncycastle.jcajce.provider.**

# A stack trace in the diagnostic log is unreadable once R8 has renamed the
# exception class: a real log line read "b70" and said nothing. Keeping the
# names costs nothing, they are already in the release mapping.
-keepnames class * extends java.lang.Throwable
