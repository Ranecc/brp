# Contributing to BRP

First, the vibe: **be relaxing.** This is ERP for Minecraft — the absurdity is
load-bearing. Read [AGENTS.md](AGENTS.md) for the house rules (including where
jokes are welcome and where they are a fireable offense).

## Getting set up

JDK 21 required (Temurin recommended).

```bash
# Backend (contract + common + backend, runs all tests)
mvn -B -ntp verify

# Mod (needs brp-common in mavenLocal first)
mvn -pl brp-common install -DskipTests
cd brp-mod && gradle build
```

Docker-less quick look at the backend stack:

```bash
cp .env.example .env            # set a real BRP_DB_PASS (compose enforces it)
docker compose up -d --build
```

## The rules that actually matter

0. **Docs are part of the change.** If your change moves behavior, the
   relevant `docs/NN-*.md` AND its `.zh.md` mirror, plus README indexes,
   update in the same commit — a code change that outruns its docs is half
   a PR (the reviewers of the future are agents; feed them). Test counts in
   README/AGENTS resync to the real surefire number when you touch tests.
1. **Contract first.** API or event changes start in `brp-contract`, then
   backend, then mod. Don't invent endpoints.
2. **Tests stay green.** `mvn -pl brp-backend -am test` must pass. If a ledger
   test breaks, everything stops until it doesn't.
3. **Docs are bilingual.** English is canonical under `docs/`; every English
   doc has a `.zh.md` mirror. An edit without its mirror is half a PR.
4. **`brp-common` stays zero-dependency. Forever. This is not a phase.**
5. **No-joke zones:** ledger invariants, migrations, auth/security code.
   Everywhere else, the meme budget is audited but generous.
6. **The loader is NeoForge 1.21.1.** Fabric proposals are read aloud in a
   silly voice and then declined (see docs/02 for the actual rationale).

## Commit style

Short, present tense, lightly unhinged:
`ledger: refuse to lose a single emerald (idempotency hardening)`

## Licensing

By contributing to `brp-backend` or `brp-contract` you agree your contributions
are licensed under **Apache-2.0**; contributions to `brp-mod` or `brp-common`
are licensed under **MIT**. See the `LICENSE` file in each module.
