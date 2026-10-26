plugins {
    java
    id("net.neoforged.moddevgradle") version "2.0.107"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

version = "0.1.0-SNAPSHOT"
group = "dev.brp"

repositories {
    mavenCentral()
    // P2 adapter work adds: maven("https://maven.blamejared.com") (Create),
    // AE2 ships via Modrinth maven; CC:T via its maven. Soft deps stay compileOnly.
}

neoForge {
    // NeoForge for MC 1.21.1 (verify latest 21.1.x on first real build)
    version = "21.1.77"

    mods {
        create("brp") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        create("server") {
            serverData()
        }
        create("client") {
            clientData()
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Single universal jar: no side classifiers, adapters are soft deps guarded
// at runtime — one artifact installs on both client and dedicated server.
