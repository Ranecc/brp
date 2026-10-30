# 15 — Deployment: compose, Kubernetes, Helm, and the single jar

> [中文版](15-deploy.zh.md)
>
> Status: **shipped** · Phase: P5 tail
> Predecessor: docs/14 (the bourse) · Successor: real machines, allegedly
>
> Four ways to run the same backend, one appendix on putting the mod jar on
> a real server. The helm chart is the performance-art finale; everything
> under it is boring, which is the only way a finale survives contact with
> an operator.

## 1. Topology matrix

| Mode | Database | Who runs the backend | Use when |
|---|---|---|---|
| **compose** (`docker-compose.yml`) | Postgres 16 container | Docker, always-on | The dev box that behaves like production |
| **k8s plain** (`deploy/k8s/`) | in-cluster StatefulSet | Kubernetes | Clusters without helm, or manifest-review cultures |
| **helm** (`deploy/helm/brp/`) | in-cluster or external | Kubernetes | Everything else — `helm install` and done |
| **single jar** (the mod jar) | H2 file fallback, or your Postgres | BackendSupervisor, in-process | The real Minecraft server |

Invariants that hold in ALL four (docs/01, AGENTS.md red lines):

- `BRP_DB_PASS` is **required**. Compose refuses to start without it; the
  helm chart refuses to render a Secret without `db.password` or
  `db.existingSecret`; the k8s manifests CrashLoopBackOff at the datasource
  without `brp-db`. Weak defaults never reach a real machine — the reminder
  is always the loudest possible one.
- `BRP_SERVER_ADDRESS` defaults to **127.0.0.1** (loopback posture). Compose
  binds host ports to 127.0.0.1; k8s sets `0.0.0.0` because the pod's
  loopback is not yours, and exposure to humans is an Ingress decision made
  on purpose.
- No Secret lives in git. Not one, not anywhere, not ever (red line 12's
  sibling rule for deployment).

## 2. Compose (the known-good baseline)

```bash
cp .env.example .env            # set a real BRP_DB_PASS
docker compose up -d --build    # postgres + backend, 127.0.0.1:8080
```

Upgrades are `git pull && docker compose up -d --build`; Flyway migrates on
boot. Redis remains intentionally absent — a service without a consumer is
an attack surface and a port.

## 3. Kubernetes plain

```bash
kubectl create namespace brp
kubectl -n brp create secret generic brp-db \
  --from-literal=BRP_DB_USER=brp --from-literal=BRP_DB_PASS=<real>
kubectl apply -f deploy/k8s/        # postgres StatefulSet + backend Deployment
kubectl -n brp port-forward svc/brp-backend 8080:8080
```

The Postgres StatefulSet is single-replica with a PVC — a Minecraft server's
ledger fits in 8 Gi the way a village fits in a minecart. The boring
enterprise answer for anything bigger is a managed database:
`postgres` out, edit `BRP_DB_URL` in `10-backend.yaml`, in.

## 4. Helm

```bash
docker build -t brp/brp-backend:latest .        # the image is the Dockerfile
helm install brp deploy/helm/brp \
  --namespace brp --create-namespace \
  --set db.password=<real>                       # or --set db.existingSecret=brp-db
```

Three render shapes, all verified by `helm lint` + `helm template`:

| values | renders |
|---|---|
| defaults + `db.password` | Secret, postgres SS+SVС, backend Deploy+SVС |
| no password, no existingSecret | **refuses to render** (`fail` in secret.yaml) |
| `postgres.enabled=false` + `db.existingSecret` + `db.url` | backend Deploy+SVС only |

External managed Postgres is `postgres.enabled=false`, `db.url=jdbc:...`,
`db.existingSecret=<yours>` — the chart does not fight your platform team,
it defers to it.

## 5. The single jar (real machine, part 1)

One file carries everything: the mod, and the backend as an embedded fat jar
at `backend/brp-backend.jar` inside the mod jar. BackendSupervisor extracts
it on first run (sha256-checked, auto-upgraded when the embedded copy
changes) and runs it as a child process.

**The packaging ritual:**

```bash
mvn -pl brp-backend -am package -DskipTests   # → brp-backend/target/brp-backend-*.jar
cd brp-mod && ../gradlew build                # embedBackend copies it in
# → brp-mod/build/libs/brp-mod-0.1.0-*.jar    # drop into <server>/mods/
```

`gradle build` without a backend jar present stays legal (mod-only dev
builds); `embedBackend` skips itself. The database fallback (docs/15 §1):
no `BRP_DB_URL` in the server's environment → the sidecar boots
`jdbc:h2:file:<server>/brp/brp-db;MODE=PostgreSQL` — a real database file
with Flyway migrations, not a toy; the backup story is "copy one file while
the server is down". Set `BRP_DB_URL`/`BRP_DB_USER`/`BRP_DB_PASS` in the
launch environment to point the sidecar at Postgres instead — the
operator's choice, never a silent default.

Verified end to end before shipping: the extracted jar boots on the H2
fallback in ~10 s, Flyway seeds the bourse (the emerald founding listing is
in the file DB), catalog/mint/clear endpoints answer.

## 6. Real machine, part 2: the mod

Drop `brp-mod-0.1.0-*.jar` into the **server's** `mods/` (NeoForge 1.21.1;
singleplayer boots nothing — the agent is a dedicated-server citizen).
System-property configuration (in `server.properties`-adjacent startup
args or the launcher script):

| Property | Meaning | Default |
|---|---|---|
| `brp.backend.url` | where the backend listens | `http://127.0.0.1:8080` |
| `brp.tenant` | the server-level tenant id | `server` |
| `brp.token` | v0 bearer token | `dev` |
| `brp.vault.container` | mint-window dispense chest `dim\|x,y,z` | unset |
| `brp.bourse.container` | bourse intake chest `dim\|x,y,z` | unset |
| `brp.catalog.push-min` | item-catalog push cadence (minutes) | `30` |
| `brp.bourse.sync-sec` | intake desk cadence (seconds) | `30` |
| `brp.dr.enabled` | DR window signal handling | `true` |

First-boot expectations, in order: `[BRP] backend sidecar not started`
(degraded mode — the agent buffers in the WAL until the backend appears) or
the supervisor's healthy log; `[BRP] agent runtime up`; the catalog push
(~5 s in, one line per batch); with an intake chest configured,
`[BRP] bourse intake desk up`. The portal renders the same numbers the
machines read; nothing in the economy is hidden behind a mod GUI, because
there is no mod GUI.

**Upgrades:** replace the mod jar — the supervisor compares the embedded
backend's sha256 with the extracted copy and swaps on change; the game's own
mods dir handles the mod half. **Backups:** compose/k8s = `pg_dump`; single
jar = stop the server, copy `brp/brp-db.mv.db` (and its `.trace.db` if
present), start the server. The ledger is the point; treat it like it.

## 7. Non-goals

- No autoscaling story: replicas defaults to 1 everywhere. The scheduled
  desks are insert-only-guarded (two runners cannot double-fire), but one
  backend keeps the cron chorus in unison and the audit trail readable.
- No TLS anywhere in this doc — TLS is a reverse proxy / Ingress decision,
  and pretending otherwise here would just be a port with a certificate.
- No database-as-a-service marketplace jokes. Managed Postgres is the
  boring enterprise answer; the chart defers to it (§4).
