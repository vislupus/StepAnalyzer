// Root build file. Plugins are applied per module (see core/ and app/) so that the
// pure-JVM :core module can be built on machines without the Android SDK.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
