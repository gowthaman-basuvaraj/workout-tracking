// Pure JVM Kotlin: models, the CSV format, the progression engine and the stats.
//
// Nothing in here may import android.* or androidx.* — that is what lets the phone app
// and the desktop CLI share one implementation instead of two that drift apart.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
