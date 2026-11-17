// Versions here are pinned as a set; bump them together, not individually.
//
// AGP 8.13.2 / compileSdk 36: androidx releases newer than the ones in app/build.gradle.kts
// (core-ktx 1.18+, activity 1.12+, compose-bom 2026.03+) require AGP 9 and compileSdk 37.
// Moving up means installing SDK platform 37 first, or the build fails a dependency check.
//
// Kotlin 2.2.20: the R8 shipped inside AGP 8.13.2 cannot parse the class metadata that
// Kotlin 2.3+ emits. It still builds, but every release build logs a wall of
// "R8: An error occurred when parsing kotlin metadata" and R8 silently skips its
// Kotlin-aware optimisations. Kotlin moves up when AGP does.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
