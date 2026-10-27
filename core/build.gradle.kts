// :core - pure Kotlin/JVM module with all signal processing and step algorithms.
// It has no Android dependencies, so it is unit-tested with plain JUnit on a desktop JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

// Desktop command-line runner: ./gradlew :core:run --args="sensors.csv track.gpx"
application {
    mainClass.set("com.vislupus.stepanalyzer.core.cli.MainKt")
}
