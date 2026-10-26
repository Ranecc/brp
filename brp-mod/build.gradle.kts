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
    mavenLocal()  // dev.brp:brp-common is installed via `mvn -pl brp-common install`
    // P2 adapter work adds: maven("https://maven.blamejared.com") (Create),
    // AE2 ships via Modrinth maven; CC:T via its maven. Soft deps stay compileOnly.
}

dependencies {
    // Shared annotation kit (mirrors backend semantics; docs/06 section 7).
    // Processing on the agent is hand-rolled (no Spring): the WAL/local store
    // implements the same Idempotent/Atomic contracts.
    implementation("dev.brp:brp-common:0.1.0-SNAPSHOT")

    // Embedded local persistence + cache. Jar-in-jar via ModDevGradle's jarJar
    // (verify plugin support on first real build; fallback: shadow + relocate
    // caffeine to dev.brp.shaded.caffeine so core code only sees CacheFront).
    // jarJar("org.xerial:sqlite-jdbc:[3.46,)")
    // jarJar("com.github.ben-manes.caffeine:caffeine:[3.2,)")
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
