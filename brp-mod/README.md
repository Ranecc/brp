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
├── spi/                 # adapter SPI — pure Java, NO Minecraft imports (testable, portable to 26.x)
│   ├── BrpAdapter.java
│   ├── InventoryProvider.java
│   ├── MachineProbe.java
│   └── SignalPort.java
├── client/              # backend API: feign-style declarative interface + JDK HttpClient impl
│   ├── BrpServerApi.java
│   └── HttpBrpClient.java
├── queue/
│   └── DiskBackedEventQueue.java   # agent-side outbox WAL (docs/06 §3)
└── adapters/            # guarded integration stubs (AE2 / Create / CC:T)
```

## Building from CN networks (mirror notes)

- Gradle dist: Huawei mirror (`https://mirrors.huaweicloud.com/gradle/`) —
  the official CDN crawls at ~1MB/min from CN.
- Maven Central via Aliyun (`~/.gradle/init.gradle`, see the repo wiki snippet).
- **`maven.neoforged.net` has NO mirror anywhere** (NeoForged does not
  publish to Central) and some sandboxes/regions cannot reach it at all —
  verified 2026-10 from this very box. If it is unreachable for you, local
  mod builds are a network impossibility: the CI workflow's `mod` job
  (GitHub runners reach it fine) is the compile verdict of record.

## Build (requires JDK 21 + Gradle 8.9+)

```
gradle build   # produces build/libs/brp-mod-<version>-all.jar  (single universal jar)
```

Not wired to CI yet — the AE2/Create adapters land in P2 together with their
respective compileOnly dependencies (see docs/02-integrations.md for the researched
entry points).
