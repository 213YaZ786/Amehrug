# Harness

Pure Kotlin checks run outside Android with kotlinc 2.4.0 before each
delivery. These files are not part of the Gradle build.

    K=app/src/main/java/com/amehrug/app

    kotlinc $K/diagnostics/DiagnosticLog.kt tools/harness/DiagnosticLogCheck.kt \
        -include-runtime -d /tmp/log.jar && java -jar /tmp/log.jar

    kotlinc $K/model/*.kt tools/harness/ModelCheck.kt \
        -include-runtime -d /tmp/model.jar && java -jar /tmp/model.jar

    kotlinc $K/crypto/Hex.kt $K/crypto/KeyEnvelope.kt $K/crypto/FileCrypto.kt \
        tools/harness/CryptoCheck.kt -include-runtime -d /tmp/crypto.jar \
        && java -jar /tmp/crypto.jar

    kotlinc $K/model/*.kt $K/crypto/Hex.kt $K/crypto/KeyEnvelope.kt \
        $K/crypto/FileCrypto.kt $K/crypto/BackupCrypto.kt \
        tools/harness/BackupCheck.kt -include-runtime -d /tmp/backup.jar \
        && java -jar /tmp/backup.jar

`tools/harness/stubs` holds compile only copies of the Room, coroutines and
Android signatures the data layer uses. They check that the data layer is
consistent with itself, not that the real libraries match:

    kotlinc tools/harness/stubs/*.kt $K/model/*.kt $K/data/*.kt $K/data/db/*.kt $K/backup/*.kt $K/security/AppLock.kt \
        $K/crypto/*.kt $K/AppGraph.kt $K/diagnostics/DiagnosticLog.kt $K/diagnostics/Diagnostics.kt \
        -d /tmp/stubcheck
