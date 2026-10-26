# Security Policy

## Reporting a vulnerability

**Do not open a public issue for security problems.** Use GitHub's private
vulnerability reporting: *Security → Report a vulnerability* on this repository.

Please include reproduction steps and affected versions. We respond in game
ticks (20 per second — the ledger demands precision, though the humans in the
loop are slower and we apologize for their refresh rate).

## Scope

In scope:

- `brp-backend` — authentication, tenant isolation, the double-entry ledger,
  anything that could mint/destroy emeralds or cross tenant boundaries
- `brp-mod` — the agent's code-execution surface, backend↔agent transport
- `brp-contract` — contract-level ambiguity that leads to the above

Out of scope:

- Minecraft itself, NeoForge, or Mojang's infrastructure
- Your own server's economy collapsing because someone found a villager
  trading exploit. That's not a vulnerability, that's macroeconomics.
- Running this backend as the ERP of a real company (see README Disclaimer;
  you were warned twice now)

## Supported versions

| Version | Supported |
|---|---|
| main branch | ✅ |
| tagged releases | ✅ latest only |
| everything else | the ledger remembers, but we don't support it |
