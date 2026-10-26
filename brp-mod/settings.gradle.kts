pluginManagement {
    repositories {
        // ModDevGradle publishes to the NeoForge maven, NOT the Gradle
        // Plugin Portal (the portal 404s on the marker artifact).
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForge"
        }
        gradlePluginPortal()
    }
}

rootProject.name = "brp-mod"
