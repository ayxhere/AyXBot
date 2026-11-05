plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // NOTE: org.jetbrains.kotlin.plugin.compose does NOT exist at Kotlin 1.9.24
    // (it arrived with Kotlin 2.0). Compose compiler is pinned via
    // composeOptions.kotlinCompilerExtensionVersion = "1.5.14" in app/build.gradle.kts.
}
