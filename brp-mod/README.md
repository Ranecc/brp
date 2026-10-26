# brp-mod — universal single-jar Minecraft mod (NeoForge 1.21.1)

One jar, two runtimes, zero player confusion:

- **Dedicated server**: the jar runs the **Agent** — event collection, inventory/machine
  probes, signal execution, and the disk-backed outbox queue (docs/01-architecture.md).
- **Player client**: the same jar runs the **Client UI** — read-only dashboards,
  approvals, order entry. The client NEVER writes state directly; it talks through
  the server-side agent (trust model, docs/00-overview.md).
- **Single-player / LAN**: runs inert with a friendly notice (backend required).

## Packaging rules

1. **One artifact, no side-specific builds.** Side detection at runtime via
   `FMLEnvironment.dist` / event bus scope; NeoForge's `@EventBusSubscriber` +
   dist filtering handles registration split naturally.
2. **Soft dependencies only**: AE2 / Create 6 / CC:Tweaked integration classes load
   lazily behind `ModList.get().isLoaded(...)` guards. The universal jar runs fine
   without any tech mod installed.
3. **Zero shaded HTTP deps**: the backend client is JDK `java.net.http` + Gson
   (both already on the Minecraft classpath). No jar-in-jar, no relocation,
   no classloader drama.

## Layout

```
src/main/java/dev/brp/mod/
├── BrpMod.java          # entry point; wires agent or client runtime by dist
├── acl/                 # anti-corruption layer ports — pure Java, NO Minecraft imports
│   └── Acl.java         #   (PlayerDirectory, GameThreads, GameEvents, WorldAccess, TeamProvider)
├── platform/neoforge/   # the only package allowed to import MC/NeoForge types
│   └── NeoForgeAcl.java #   implements acl ports on the NeoForge event bus
├── agent/
│   └── AgentRuntime.java        # one-way pipeline: ACL → WAL → IO batches → backend ack → watermark
├── client/              # backend API: feign-style declarative interface + JDK HttpClient impl
│   ├── BrpServerApi.java
│   └── HttpBrpClient.java
├── queue/
│   └── DiskBackedEventQueue.java   # agent-side outbox WAL (docs/06 §3)
├── store/               # embedded persistence behind CacheFront (local read caching)
│   ├── LocalStore.java
│   ├── SqliteLocalStore.java
│   └── CacheFront.java
├── supervisor/
│   └── BackendSupervisor.java      # child-process sidecar lifecycle: extract, spawn, health, restart budget
├── spi/                 # adapter SPI — pure Java (P2 tech-mod adapters implement this)
│   ├── BrpAdapter.java
│   ├── InventoryProvider.java
│   ├── MachineProbe.java
│   └── SignalPort.java
└── adapters/            # guarded integration stubs (FTB Teams now; AE2 / Create / CC:T in P2)
```

## Building from CN networks (mirror notes)

- Gradle dist: Huawei mirror (`https://mirrors.huaweicloud.com/gradle/`) —
  the official CDN crawls at ~1MB/min from CN.
- Maven Central via Aliyun (`~/.gradle/init.gradle`).
- **`maven.neoforged.net` → `https://neoforged.forgecdn.net/releases`**: the
  forgecdn host mirrors the identical `/releases` tree (same paths, same
  artifacts — ModDevGradle plugin, neoforge userdev, neoform, everything)
  and is reachable from networks where the origin maven is not. Two layers:
  `settings.gradle.kts` points pluginManagement at the CDN directly, and a
  `~/.gradle/init.gradle` live rule rewrites every reference to the dead
  host — including repositories ModDevGradle adds internally. Wipe
  `~/.gradle/caches` once after switching, or stale negative lookups
  (Aliyun's 404s, cached) will keep lying to you. A lesson purchased the
  hard way, twice.
- Local builds are the compile verdict of record again. CI remains the
  second opinion, as all good enterprise processes demand.

## Build (requires JDK 21 + Gradle 8.9+)

```
gradle build   # produces build/libs/brp-mod-<version>.jar  (single universal jar)
```

Wired to CI (`.github/workflows/ci.yml`, `mod` job) after `brp-common` is
installed via `mvn -pl brp-common install`. The AE2/Create adapters land in
P2 together with their respective compileOnly dependencies (see
docs/02-integrations.md for the researched entry points).
