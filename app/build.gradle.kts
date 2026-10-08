import java.util.Properties

/**
 * Where the Android app looks for the FastAPI server.
 *
 * The committed default (127.0.0.1) is correct for a *physical device* bridged
 * with `adb reverse tcp:8000 tcp:8000`, but it is wrong in two other common
 * setups, and both must be overridden per machine in `local.properties`
 * (gitignored, never committed) rather than by editing this default:
 *
 *   - Emulator: 127.0.0.1 there means the emulator's own loopback, not the
 *     host — use the special host-loopback address instead:
 *         krishinirnay.serverBaseUrl=http://10.0.2.2:8000/
 *
 *   - Physical device over Wi-Fi, *without* `adb reverse` active (adb reverse
 *     silently drops on USB disconnect/reconnect, device reboot, or `adb
 *     kill-server` — when that happens 127.0.0.1 from the device means the
 *     device itself, not this PC, and every request fails to connect):
 *     use this machine's actual LAN IP (`ipconfig` -> IPv4 Address) instead,
 *     e.g.:
 *         krishinirnay.serverBaseUrl=http://10.212.224.148:8000/
 *     This requires the FastAPI server to bind 0.0.0.0 (not 127.0.0.1) and
 *     the PC's firewall to allow inbound TCP on port 8000 from the LAN.
 */
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

val serverBaseUrl: String = localProps.getProperty("krishinirnay.serverBaseUrl") ?: "http://127.0.0.1:8000/"

// Must match the server's API_KEY env var. A deployed server (render.yaml)
// sets a real key, so override per machine in local.properties:
//     krishinirnay.serverApiKey=<same value as the server's API_KEY>
val serverApiKey: String = localProps.getProperty("krishinirnay.serverApiKey") ?: "dev-only-change-me"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.krishinirnay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.krishinirnay"
        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // ============================================================
        // LOCAL FASTAPI SERVER
        // ============================================================
        //
        // Physical device (default): ADB reverse -> PC FastAPI
        //     adb reverse tcp:8000 tcp:8000
        //     -> 127.0.0.1:8000 works
        //
        // Emulator: 127.0.0.1 is the EMULATOR's loopback, not the PC.
        //     Put this in local.properties instead:
        //     krishinirnay.serverBaseUrl=http://10.0.2.2:8000/
        //
        // See `serverBaseUrl` at the top of this file.
        // ============================================================

        buildConfigField(
            "String",
            "SERVER_BASE_URL",
            "\"$serverBaseUrl\""
        )

        // Must match FastAPI API key — see `serverApiKey` at the top of this file.
        buildConfigField(
            "String",
            "SERVER_API_KEY",
            "\"$serverApiKey\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Event demo build: installable without a release keystore.
            signingConfig = signingConfigs.getByName("debug")

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Repositories under test (e.g. LiveFieldStateRepositoryImpl) call
    // android.util.Log directly; without this, any unmocked android.jar
    // call throws in a plain local unit test instead of returning a
    // default value.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // Material XML theme support
    implementation(libs.androidx.material)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profiles Compose ships with on sideloaded APKs — faster startup + scrolling.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    // Farmer profile documents (Phase 4) — replaces the never-configured,
    // never-used firebase-database-ktx (no databaseURL in google-services.json,
    // zero references in the codebase) rather than adding a second cloud DB.
    implementation(libs.firebase.firestore.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // ONNX Runtime
    implementation(libs.onnxruntime.android)

    // On-device LLM inference (Phase 5 Part 3) — Google's LiteRT-LM runtime
    // (the actively-maintained successor to the older, now-deprecated
    // MediaPipe tasks-genai/LlmInference API) for running a small local
    // model (Gemma 3 1B int4) directly on the phone, no PC/FastAPI/Ollama
    // required. See MediaPipeOnDeviceLlmProvider.
    implementation(libs.litertlm.android)

    // Image loading
    implementation(libs.coil.compose)

    // Retrofit
    implementation(libs.retrofit.core)
    implementation(
        libs.retrofit.kotlinx.serialization.converter
    )

    // OkHttp
    implementation(libs.okhttp.logging.interceptor)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumentation tests
    androidTestImplementation(
        libs.androidx.test.ext.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )
}