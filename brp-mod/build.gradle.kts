plugins {
    java
    // Correct plugin id: net.neoforged.moddev (NOT ...moddevgradle — the
    // marker artifact for that id exists nowhere, portal or NeoForge maven).
    id("net.neoforged.moddev") version "2.0.148"
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

    // Pure-Java units (governor decision tables etc.) run as plain JUnit —
    // no Minecraft bootstrap, no client, no excuses.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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

// Single-file distribution: the release pipeline drops the Maven-built fat
// jar at src/main/resources/backend/brp-backend.jar (git-ignored) before
// `gradle build`; BackendSupervisor extracts it at first run (sha256-checked,
// auto-upgraded when the embedded copy changes). 100+ MB mod jars are a
// time-honored tradition; we uphold it.
//
// tasks.register("embedBackend", Copy::class) {
//     from(rootProject.projectDir.resolve("brp-backend/target/brp-backend-0.1.0-SNAPSHOT.jar"))
//     into(layout.projectDirectory.dir("src/main/resources/backend"))
//     rename { "brp-backend.jar" }
// }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
