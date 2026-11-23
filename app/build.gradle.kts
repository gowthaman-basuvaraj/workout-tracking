plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing material comes from the environment (injected by Jenkins) or from -P
// Gradle properties — never from a file in the repo. Absent for local and debug builds,
// in which case the release APK is simply left unsigned.
fun secret(name: String): String? =
    System.getenv(name) ?: providers.gradleProperty(name).orNull

val releaseStoreFile: String? = secret("RELEASE_STORE_FILE")

/**
 * The version code is the number of commits, which only ever goes up and is the same
 * number whoever builds it -- Jenkins, a laptop, anyone.
 *
 * Play enforces this one harder than anything else in the manifest: an upload whose
 * version code is not strictly higher than the last is rejected, and a number once used
 * can never be reused. So the failure mode to design against is not "wrong", it is
 * "wrong and quiet". Both ways of getting it wrong therefore stop the build.
 *
 * Not Jenkins' BUILD_NUMBER: that is per-job, so recreating the job resets it to 1 and
 * every later upload is refused until the count climbs past whatever was already used.
 */
private val MIN_VERSION_CODE = 24   // the commit count when this was wired up

private fun git(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", "-C", rootDir.absolutePath, *args) }
        .standardOutput.asText.get().trim().ifBlank { null }
}.getOrNull()

val gitVersionCode: Int = run {
    val count = git("rev-list", "--count", "HEAD")?.toIntOrNull()
        ?: error(
            "Cannot read the git history, so there is no version code to use. Build from " +
                "a git checkout: a guessed number here is either rejected by Play or burns " +
                "a version that can never be used again."
        )
    // A shallow clone answers 1, which would look perfectly normal and be permanent.
    check(count >= MIN_VERSION_CODE) {
        "git says $count commits, which is below the $MIN_VERSION_CODE this project had " +
            "when versioning was wired up. That means a shallow clone (--depth). Fetch " +
            "the full history; the version code must count every commit."
    }
    count
}

/** The latest tag if there is one, so the listing shows something a person chose. */
val gitVersionName: String = git("describe", "--tags", "--abbrev=0")
    ?.removePrefix("v")
    ?: "1.0"

android {
    namespace = "com.gowthaman.workout"
    compileSdk = 36

    defaultConfig {
        // Reverse-DNS of uncomplicated.life. Permanent: Play binds the listing to this
        // string on first upload, so it can never change without becoming a new app.
        // Deliberately not the namespace -- that stays com.gowthaman.workout so no
        // source file or import has to move.
        applicationId = "life.uncomplicated"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode
        versionName = gitVersionName
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign only when a keystore was supplied; otherwise AGP emits
            // app-release-unsigned.apk and the CI release stage fails loudly.
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

/**
 * One command for a release, because a release is two artifacts now.
 *
 * The APK is what you sideload onto your own phone or hand to someone directly; the
 * bundle is the only format Play accepts for a new app. They are built from the same
 * signed, minified output, so building one without the other just means the pair can
 * drift.
 */
tasks.register("release") {
    group = "build"
    description = "Builds both release artifacts: the sideload APK and the Play bundle."
    dependsOn("assembleRelease", "bundleRelease")
}
