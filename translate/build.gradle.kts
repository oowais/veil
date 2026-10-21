plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.owaisahmed.veil.translate"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 30
        // -PveilAbi=x86_64 builds for the emulator (README screenshots); phones are arm64 only.
        ndk { abiFilters += (findProperty("veilAbi") as String? ?: "arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_STL=c++_shared",
                    "-DBUILD_SHARED_LIBS=ON",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DGGML_NATIVE=OFF",
                    // One CPU backend per ARM feature level (dotprod, i8mm, sve...), picked at runtime.
                    "-DGGML_BACKEND_DL=ON",
                    "-DGGML_CPU_ALL_VARIANTS=ON",
                    "-DGGML_LLAMAFILE=OFF",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The OPUS-MT tests compare against the real model files when these point at them.
    testOptions.unitTests.all {
        it.environment("VEIL_OPUS_DIR", System.getenv("VEIL_OPUS_DIR") ?: "")
        it.environment("VEIL_OPUS_MODEL", System.getenv("VEIL_OPUS_MODEL") ?: "")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
    // Desktop ONNX Runtime (same API, with x86-64 natives) so MarianTranslatorTest runs on the JVM.
    testImplementation(libs.onnxruntime.jvm)
}
