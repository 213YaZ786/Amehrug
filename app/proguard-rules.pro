# Project rules. Compose and AndroidX ship their own consumer rules.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# SQLCipher: the Java classes are reached from JNI.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
