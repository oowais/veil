plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.owaisahmed.veil"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.owaisahmed.veil"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // Committed on purpose: lets CI-built debug APKs install over each other. Debug builds only.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned until a release keystore exists; CI only checks that it assembles.
            signingConfig = null
        }
        // Release-optimised but debug-signed: for measuring real speed on device. Not minified so it
        // installs over / next to debug builds without R8 surprises.
        create("profile") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // ggml loads its CPU backend variants from nativeLibraryDir at runtime, so the .so files must be extracted.
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(project(":trigger"))
    implementation(project(":ocr"))
    implementation(project(":layout"))
    implementation(project(":translate"))
    implementation(project(":models"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
