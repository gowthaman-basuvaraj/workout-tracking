// Shared Kotlin: models, the CSV format, the progression engine and the stats.
//
// Multiplatform rather than plain JVM, so the same logic can one day back an iOS app
// without a second implementation of anything. Nothing in commonMain may import
// android.*, androidx.* or java.* — the first two would break the CLI, and the third
// would break iOS. That rule is the whole reason this module exists.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(17)

    jvm {
        // The app and the CLI both consume this target.
        testRuns["test"].executionTask.configure { useJUnit() }
    }

    // iOS is only declared on a Mac, because Kotlin/Native cannot build Apple targets on
    // any other host. Declaring them unconditionally would break every Windows and Linux
    // build of the project for the sake of a target they cannot compile anyway.
    // wasm has neither a JVM nor an Android runtime underneath it, so it catches a stray
    // java.* import the way the native target does -- and unlike the native target it is
    // host-independent, which is what lets :ui share the same guard on any machine.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    val os = System.getProperty("os.name")
    when {
        os.startsWith("Mac") -> {
            iosArm64()
            iosSimulatorArm64()
        }
        // A native canary on every other host. The JVM target cannot catch a java.*
        // import in commonMain -- java.* is right there on the JVM -- so without a
        // non-JVM target the "no java.* in common" rule is a comment nobody enforces.
        // This one compiles locally and fails the build the moment it is broken.
        os.startsWith("Windows") -> mingwX64()
        else -> linuxX64()
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: LocalDate is all over this module's public
            // signatures, so the app and the CLI need it on their own classpath too.
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

            // Settings and the repository expose StateFlow, so this is api too. The
            // version matches what the Android app already resolves to, so :core does
            // not drag a second copy of coroutines onto its classpath.
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        // Tests stay on JUnit4 and JVM-only for now. They exercise commonMain all the
        // same, which is what makes this refactor safe; moving them to kotlin.test is a
        // separate job with its own trap (assertTrue's message argument swaps sides).
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}
