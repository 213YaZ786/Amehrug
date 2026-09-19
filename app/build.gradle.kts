// Kotlin is built into AGP 9, so there is no kotlin-android plugin here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.amehrug.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.amehrug.app"
        minSdk = 31
        targetSdk = 37
        versionCode = 23
        versionName = "0.14.0"
    }

    // Signing comes from the environment, so no key and no password is ever
    // in the repository. Without them the release build is simply unsigned,
    // which keeps a local build working.
    val keystore = providers.environmentVariable("AMEHRUG_KEYSTORE").orNull

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("AMEHRUG_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("AMEHRUG_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("AMEHRUG_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // One APK per architecture, plus a universal one for anything else.
    // SQLCipher ships four native builds, and they are most of the size.
    // If this DSL ever moves, removing the whole block leaves one working
    // APK and nothing else changes.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // Google's encrypted dependency report is not wanted in the APK.
    // F-Droid rejects it as well.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room writes one JSON file per schema version here, in a folder per variant.
// Commit them: they are the reference for every future migration.
//
// The Room plugin does this, not `ksp { arg("room.schemaLocation") }`: with
// the ksp argument, the debug and release tasks write the same file at the
// same time, and the release build read a half written file. That is what
// broke the first CI run.
room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    // Versions and the pairing with androidx.sqlite come from the SQLCipher
    // for Android README, read on 2026-09-17.
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // Argon2id for the backup password. Android has no memory hard key
    // derivation of its own, and OWASP puts Argon2id first.
    implementation(libs.bouncycastle)
}
