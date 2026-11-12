// Pinned to the compileSdk 36 generation on purpose: androidx releases newer than these
// (core-ktx 1.18+, activity 1.12+, compose-bom 2026.03+) require AGP 9 and compileSdk 37,
// which this machine's SDK does not have installed. Bump all of them together, after
// installing platform 37, or the build fails on a dependency-constraint check.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
