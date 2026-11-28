// The screens, built once for every platform that will show them.
//
// Compose Multiplatform 1.10.3 rather than the current 1.12: 1.12 pulls androidx.compose
// 1.12, which requires compileSdk 37 and AGP 9.1, and this project is pinned below both
// for reasons recorded in the root build file. 1.10.3 lines up with the androidx.compose
// the app already resolves, so nothing else has to move.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
    androidTarget()

    // The canary, and the reason this module can be trusted. Shared UI must not reach for
    // a Context, a Toast or java.time, and the Android target compiles all three without
    // complaint -- it has them. wasm has neither runtime underneath it, so it fails the
    // build instead, with a line number. It is never shipped; it exists to be compiled.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            // Only the core icon set: every icon these screens use is in it. The extended
            // set is the same icons plus about two thousand more, and costs 15 MB of dex
            // that R8 only removes from release builds -- debug builds carry all of it.
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation(compose.ui)

            // JetBrains' multiplatform builds of these, not Google's Android-only ones.
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
        }

        androidMain.dependencies {
            // Everything here is a platform seam: the folder picker and the permission
            // prompt both need an Activity result launcher, and the lock needs the
            // framework BiometricPrompt.
            implementation("androidx.activity:activity-compose:1.11.0")
            implementation("androidx.core:core-ktx:1.17.0")
        }
    }
}

android {
    namespace = "com.gowthaman.workout.sharedui"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
