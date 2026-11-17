// The desktop logger. Produces a self-contained jar so it runs on any machine with a
// JDK and nothing else installed:  java -jar jotter.jar coffee
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.gowthaman.workout.cli.MainKt")
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

tasks.jar {
    archiveBaseName.set("jotter")
    archiveVersion.set("")
    manifest { attributes["Main-Class"] = "com.gowthaman.workout.cli.MainKt" }
    // Fold the Kotlin stdlib and :core in, so there is no classpath to assemble by hand.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/LICENSE*")
}
