plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release version from CI (`-PveilVersion=0.x.y`, D-019); local builds are 0.0.1.
val veilVersion = providers.gradleProperty("veilVersion").getOrElse("0.0.1")
val veilVersionCode = Regex("""0\.(\d+)\.(\d+)""").matchEntire(veilVersion)
    ?.destructured?.let { (minor, patch) -> minor.toInt() * 1000 + patch.toInt() }
    ?: error("veilVersion must be 0.x.y, got $veilVersion")

// Secret release key (D-026), only in CI: VEIL_KEYSTORE_PATH + VEIL_KEYSTORE_PASSWORD. Without it the
// release APK stays unsigned.
val releaseKeystore = providers.environmentVariable("VEIL_KEYSTORE_PATH").orNull

android {
    namespace = "com.owaisahmed.veil"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.owaisahmed.veil"
        minSdk = 30
        targetSdk = 35
        versionCode = veilVersionCode
        versionName = veilVersion
        manifestPlaceholders["appLabel"] = "Veil"
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
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("VEIL_KEYSTORE_PASSWORD").get()
                keyAlias = "veil"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        // Debug-key builds get their own app id (D-028): they install next to the release app.
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Veil dev"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Release-optimised but debug-signed: for measuring real speed on device. Not minified so it
        // installs over / next to debug builds without R8 surprises.
        create("profile") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Veil dev"
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
    implementation(project(":capture"))
    implementation(project(":overlay"))

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

    testImplementation(libs.junit)
}
