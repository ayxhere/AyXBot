import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // No kotlin.plugin.compose here — that plugin does not exist at Kotlin 1.9.24.
    // Compose compiler version is pinned via composeOptions below (1.5.14).
}

android {
    namespace = "com.ayx.bot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ayx.bot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release signing via env vars (GitHub Actions secrets). Debug unaffected.
            val ksBase64 = System.getenv("AYXBOT_KEYSTORE_BASE64")
            val ksPass = System.getenv("AYXBOT_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("AYXBOT_KEY_ALIAS")
            val keyPass = System.getenv("AYXBOT_KEY_PASSWORD")
            if (!ksBase64.isNullOrEmpty() && !ksPass.isNullOrEmpty()
                && !keyAlias.isNullOrEmpty() && !keyPass.isNullOrEmpty()
            ) {
                val ksFile = File(buildDir, "ayxbot-release.keystore")
                ksFile.writeBytes(Base64.getDecoder().decode(ksBase64))
                signingConfig = signingConfigs.create("release") {
                    storeFile = ksFile
                    storeType = "PKCS12"
                    storePassword = ksPass
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPass
                }
            }
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    // Material (XML theme support + widgets) — mandatory per AyX checklist
    implementation("com.google.android.material:material:1.12.0")

    // Encrypted key storage (Groq / OpenRouter keys)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Network (Groq + OpenRouter + Whisper + model downloads)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
