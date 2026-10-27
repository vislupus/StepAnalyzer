// Root settings for the Step Analyzer project.
//
// Two modules:
//   :core  - pure Kotlin/JVM: signal processing, step algorithms, step-length models,
//            CSV/GPX parsing and the analysis pipeline. No Android dependency, so it can
//            be unit-tested on any machine with a JDK.
//   :app   - the Android application (Jetpack Compose UI, SQLite, sensors, GPS).
//
// Pass -PcoreOnly to Gradle to configure only :core (handy on machines without the
// Android SDK, e.g. a CI runner that only runs the algorithm tests):
//     ./gradlew -PcoreOnly :core:test

pluginManagement {
    repositories {
        if (!providers.gradleProperty("coreOnly").isPresent) google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!providers.gradleProperty("coreOnly").isPresent) google()
        mavenCentral()
    }
}

rootProject.name = "StepAnalyzer"

val coreOnly = providers.gradleProperty("coreOnly").isPresent

include(":core")
if (!coreOnly) include(":app")
