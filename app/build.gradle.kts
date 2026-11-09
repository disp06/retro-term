plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.disp06.retroterm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.disp06.retroterm"
        minSdk = 26          // Wear OS 2 devices are Android 8+; target device is API 28
        targetSdk = 28       // Wear OS 2.26 / Android 9 — keep old-platform behaviour
        versionCode = 3
        versionName = "2.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideload-friendly: signed with the debug key. Swap for your own keystore if you publish.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        checkReleaseBuilds = false
    }
}

dependencies {
    // Watch face API (CanvasWatchFaceService) for Wear OS 2
    implementation("com.google.android.support:wearable:2.9.0")
    compileOnly("com.google.android.wearable:wearable:2.9.0")
    // WearableRecyclerView
    implementation("androidx.wear:wear:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.12.0")
}
