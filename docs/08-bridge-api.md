# 08 — Bridge Mod API (external integrations)

> [中文版](08-bridge-api.zh.md)
>
> BRP exposes a STABLE surface for third-party bridge mods: other players
> can wire their mods (tech, magic, logistics — anything) into the ledger,
> quotas and signals without shading our internals. If it isn't in this
> document, it isn't API.

## 1. Mod-side surface (`dev.brp.api`, semver, additive-only)

```java
// entrypoint discovered via Java ServiceLoader (no NeoForge coupling)
public interface BrpBridge {
    void publish(EventDto event);                 // -> agent WAL, at-least-once
    CompletableFuture<Double> stock(String itemId); // team-scoped read
    CompletableFuture<Boolean> signal(String channel, int level0to15);
    void onLedgerEvent(Consumer<LedgerEvent> consumer);  // fan-out of booked events
}
```

- Bridge mods depend on `brp-common` only (annotations: `@Idempotent`
  semantics honored at the WAL boundary) — zero shaded dependencies.
- `EventDto` reuses the telemetry envelope verbatim; event types prefixed
  `ext.<modid>.*` to keep the registry clean.
- Publishing NEVER blocks the caller: the bridge hands off to the WAL and
  returns (red line 3 applies to guests too).
- Versioning: `dev.brp.api` follows semver; breaking changes spawn `v2`
  package and a two-version freeze (same discipline as event schemas).

## 2. Backend-side surface

Third-party integrations use the SAME REST contract (brp-contract) with
scoped machine tokens (provisioned like agents, per-tenant read or
command scope; P2). No private endpoints, ever — the OpenAPI file is the
whole truth.

## 3. Governance

- Bridge mod registration is contract-first: an `ext.*` event type lands in
  events.md before code ships.
- Abuse posture: bridge events are TELEMETRY-grade by default; anything
  money-touching must go through an explicitly provisioned CRITICAL scope
  with its own audit trail.
- First-party consumers of this API: the Create/AE2/CC:T adapters
  themselves (docs/02) — we eat our own bridge.
