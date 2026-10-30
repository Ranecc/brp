# 15 — 部署：compose、Kubernetes、Helm 与单 jar

> [English](15-deploy.md)
>
> 状态：**已交付** · 阶段：P5 尾声
> 前置：docs/14（交易所）· 后继：真机（据说要来了）
>
> 同一个后端的四种跑法，外加一节「把 mod jar 放上真服务器」。helm chart
> 是行为艺术收尾；它底下的每一行都很无聊——收尾曲想熬过运维的手，
> 只有无聊这一条路。

## 1. 拓扑矩阵

| 模式 | 数据库 | 后端由谁跑 | 何时用 |
|---|---|---|---|
| **compose**（`docker-compose.yml`） | Postgres 16 容器 | Docker，常驻 | 像生产环境的开发机 |
| **k8s 裸清单**（`deploy/k8s/`） | 集群内 StatefulSet | Kubernetes | 没有 helm 的集群，或清单评审文化 |
| **helm**（`deploy/helm/brp/`） | 集群内或外部 | Kubernetes | 其余一切——`helm install` 完事 |
| **单 jar**（mod jar） | H2 文件兜底，或你的 Postgres | BackendSupervisor 进程内 | 真正的 Minecraft 服务器 |

四模式共通的不变量（docs/01、AGENTS.md 红线）：

- `BRP_DB_PASS` **必填**。compose 没它拒绝启动；helm chart 没有密码或
  `db.existingSecret` 拒绝渲染；k8s 清单没有 `brp-db` 直接在数据源处
  CrashLoopBackOff。弱默认值永远到不了真机——提醒方式永远是最响的那种。
- `BRP_SERVER_ADDRESS` 默认 **127.0.0.1**（回环姿态）。compose 把宿主
  端口绑在 127.0.0.1；k8s 设 `0.0.0.0`，因为 Pod 的回环不是你的回环，
  对人暴露是 Ingress 层面特意做的决定。
- git 里没有任何 Secret。一个没有，哪儿都没有，永远没有（红线 12 的
  部署篇姊妹条款）。

## 2. Compose（久经考验的基线）

```bash
cp .env.example .env            # 填一个真的 BRP_DB_PASS
docker compose up -d --build    # postgres + backend，127.0.0.1:8080
```

升级就是 `git pull && docker compose up -d --build`；Flyway 开机自动迁移。
Redis 依然刻意缺席——没有消费者的服务只是一个攻击面加一个端口。

## 3. Kubernetes 裸清单

```bash
kubectl create namespace brp
kubectl -n brp create secret generic brp-db \
  --from-literal=BRP_DB_USER=brp --from-literal=BRP_DB_PASS=<真密码>
kubectl apply -f deploy/k8s/        # postgres StatefulSet + backend Deployment
kubectl -n brp port-forward svc/brp-backend 8080:8080
```

Postgres StatefulSet 单副本带 PVC——一个 MC 服务器的账本 8 Gi 装得下，
就像一个村子装得进一辆矿车。再大就该走无聊的企业答案：托管数据库——
把 postgres 摘掉，改 `10-backend.yaml` 里的 `BRP_DB_URL`，收工。

## 4. Helm

```bash
docker build -t brp/brp-backend:latest .        # 镜像就是那个 Dockerfile
helm install brp deploy/helm/brp \
  --namespace brp --create-namespace \
  --set db.password=<真密码>                     # 或 --set db.existingSecret=brp-db
```

三种渲染形态，全部经 `helm lint` + `helm template` 验证：

| values | 渲染结果 |
|---|---|
| 默认 + `db.password` | Secret、postgres SS+SVС、backend Deploy+SVС |
| 无密码且无 existingSecret | **拒绝渲染**（secret.yaml 里的 `fail`） |
| `postgres.enabled=false` + `db.existingSecret` + `db.url` | 只有 backend Deploy+SVС |

外部托管 Postgres 就是 `postgres.enabled=false`、`db.url=jdbc:...`、
`db.existingSecret=<你的>`——chart 不和你的平台团队打架，它让贤。

## 5. 单 jar（真机·上）

一个文件装下一切：mod 本体，以及作为嵌入式 fat jar 的后端
（mod jar 内的 `backend/brp-backend.jar`）。BackendSupervisor 首次运行时
解包（sha256 校验，嵌入副本变化时自动升级），并以子进程方式运行。

**打包仪式：**

```bash
mvn -pl brp-backend -am package -DskipTests   # → brp-backend/target/brp-backend-*.jar
cd brp-mod && ../gradlew build                # embedBackend 自动嵌入
# → brp-mod/build/libs/brp-mod-0.1.0-*.jar    # 丢进 <server>/mods/
```

没有后端 jar 时 `gradle build` 依然合法（纯 mod 开发构建）；embedBackend
会自己跳过。数据库兜底（docs/15 §1）：服务器启动环境没有 `BRP_DB_URL` →
sidecar 以 `jdbc:h2:file:<server>/brp/brp-db;MODE=PostgreSQL` 启动——
带 Flyway 迁移的真数据库文件，不是玩具；备份口径是「停服，拷走一个文件」。
在启动环境里设 `BRP_DB_URL`/`BRP_DB_USER`/`BRP_DB_PASS` 可把 sidecar 指向
Postgres——运维的选择，绝不偷偷替你做默认。

交付前已端到端验证：解包 jar 以 H2 兜底约 10 秒启动，Flyway 播种交易所
（emerald 开市挂牌行就在文件库里），catalog/mint/clear 端点全部应答。

## 6. 真机·下：mod

把 `brp-mod-0.1.0-*.jar` 放进**服务器**的 `mods/`（NeoForge 1.21.1；
P6 起单人模式同样启动 agent——本地 ERP，见 docs/16——
`brp.agent.enabled=false` 是总开关）。系统属性配置
（启动参数或启动脚本里给）：

| 属性 | 含义 | 默认 |
|---|---|---|
| `brp.backend.url` | 后端监听地址 | `http://127.0.0.1:8080` |
| `brp.tenant` | 服务器级租户 id | `server` |
| `brp.token` | v0 bearer token；未配置时自动铸造并持久化到 `brp/agent.token` | 自动 |
| `brp.vault.container` | 铸币窗口发放箱 `dim\|x,y,z` | 未设 |
| `brp.bourse.container` | 交易所入库箱 `dim\|x,y,z` | 未设 |
| `brp.catalog.push-min` | 物品目录推送节奏（分钟） | `30` |
| `brp.bourse.sync-sec` | 入库台节奏（秒） | `30` |
| `brp.dr.enabled` | 需求响应窗口信号处理 | `true` |
| `brp.agent.enabled` | mod 总开关 | `true` |

首启预期，按顺序：`[BRP] backend sidecar not started`（降级模式——
WAL 缓冲到后端出现为止）或 supervisor 的健康日志；`[BRP] agent runtime up`；
目录推送（约 5 秒后，每批一行日志）；配置了入库箱则有
`[BRP] bourse intake desk up`。门户渲染的数字和机器读到的一样——
经济里没有任何藏在 mod 界面后面的东西，因为根本没有 mod 界面。

**升级**：换 mod jar——supervisor 比对嵌入后端的 sha256 与已解包副本，
不一致即换；mod 那一半归游戏自己的 mods 目录管。**备份**：compose/k8s
= `pg_dump`；单 jar = 停服，拷走 `brp/brp-db.mv.db`（有 `.trace.db` 也拷上），
开服。账本是全部意义所在，按这个规格对待它。

## 7. 非目标

- 没有自动扩缩容的故事：replicas 默认处处为 1。定时台都有 insert-only
  守卫（双跑不会双发），但一个后端让 cron 合唱队保持齐声、审计线可读。
- 本文没有任何 TLS——TLS 是反向代理 / Ingress 的决定，在这里假装配置它
  只会得到一个带证书的端口。
- 没有数据库即服务的marketplace笑话。托管 Postgres 是无聊的企业答案，
  chart 让贤（§4）。
