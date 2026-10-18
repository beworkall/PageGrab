plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pagegrab.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pagegrab.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 6
        versionName = "6.0"
        // arm64 only = keeps the APK small (covers practically every phone made since ~2017).
        // Need old 32-bit phones too? add "armeabi-v7a" here.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // yt-dlp / python / ffmpeg ship as native libs and must be extracted to run
    packaging { jniLibs { useLegacyPackaging = true } }

    // Release signing comes from environment variables set by CI (see .github/workflows/release.yml).
    // Nothing secret is ever committed to this repo. If the vars aren't set (e.g. a local debug
    // build), the release build just falls back to the debug key so `assembleRelease` still works.
    val ksFile = System.getenv("PAGEGRAB_KEYSTORE_PATH")
    if (ksFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("PAGEGRAB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PAGEGRAB_KEY_ALIAS")
                keyPassword = System.getenv("PAGEGRAB_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    // If this version ever fails to resolve, try "0.17.4"
    val yt = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$yt")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$yt")
    implementation("androidx.core:core-ktx:1.13.1")
}
