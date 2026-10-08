// Standalone build (not part of the Android app): runs the game's real engine and
// Compose screens on the desktop JVM so they can be playtested headlessly.
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "playtest"
