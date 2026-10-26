pluginManagement {
    repositories {
        // ModDevGradle publishes to the NeoForge maven, NOT the Gradle
        // Plugin Portal (the portal 404s on the marker artifact).
        // maven.neoforged.net is a mirror of the same /releases tree on
        // neoforged.forgecdn.net; the CDN is reachable where the origin
        // is not (CN networks, offline dev boxes). Layout is identical.
        maven("https://neoforged.forgecdn.net/releases") {
            name = "NeoForge"
        }
        gradlePluginPortal()
    }
}

rootProject.name = "brp-mod"
