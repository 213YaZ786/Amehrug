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
