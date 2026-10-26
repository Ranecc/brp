# 01 — 总体架构

> 英文版为主文档：[01-architecture.md](01-architecture.md)

## 1. 版本与加载器决策（2026-10 调研）

**结论：Minecraft 1.21.1 + NeoForge 为主目标。**

| 候选 | 证据 | 判定 |
|---|---|---|
| **1.21.1 / NeoForge** | Create 6 官方支持（wiki 有 dedicated "Depend on Create / NeoForge 1.21.1" 开发指南）；AE2 官方发行 NeoForge 1.21.1；2026-09 更新的大整合包仍以 1.21.1 为基准 | ✅ 主目标 |
| 26.1 / 26.2（年份版） | MC 2026 起改年份版本号（26.1≈旧1.22）；NeoForge 工具链已支持到 26.3；科技 mod（尤其 Create）登陆情况未明 | 🔭 前瞻，Create 官方移植后再评估 |
| 1.20.1 / Forge | 遗产生态大，但属过去 | ❌ |
| Fabric | AE2 有 Fabric 版，但 **Create 官方仅 NeoForge**，而机械手/SU/物流网络是核心需求 | ❌ 锁死 NeoForge |

版本化策略：Agent 的**核心逻辑不 import 任何 MC 类**（纯 Java，见 brp-mod/src/.../spi），
MC 交互层薄壳化，为将来 26.x 移植留缝。

## 2. 组件与部署拓扑

**单个通用 mod jar**（服务器和玩家装同一个文件），按运行环境（dist）激活：
专用服务器上启动 Agent 运行时，玩家客户端上启动 UI 运行时。

```
┌────────────────────────────── 物理机/内网 ──────────────────────────────┐
│                                                                        │
│  MC 专用服务器                BRP Backend（独立 JVM）                    │
│  ┌──────────────────────┐        ┌──────────────────────────────┐      │
│  │ BRP-Agent (NeoForge) │ HTTPS  │ Spring Boot 3.5               │      │
│  │  ├ 事件采集(玩家/方块)│──────►│  ├ REST  命令面(低频)         │      │
│  │  ├ 探针(库存/机器)   │◄──────│  ├ 批量  遥测面(高频)         │      │
│  │  ├ 信号执行(红石)    │ 轮询/ack│  ├ 推送  信号指令            │      │
│  │  └ 磁盘队列(降级)    │        │  └ PostgreSQL / Redis         │      │
│  └──────────────────────┘        └──────┬───────────────────────┘      │
│  (同一个 jar 装在玩家客户端 = UI 运行时)   │ Web                          │
│                                   玩家门户(OAuth2) + Grafana            │
└────────────────────────────────────────────────────────────────────────┘
```

为什么 Spring 不进游戏 JVM（重申，这是本项目第一铁律）：
- MC 服务端捆绑的 Netty/Guava/log4j 与 Spring Boot 依赖管理必然冲突，ModLauncher/SecureJarHandler 隔离是灾难
- 崩溃隔离：双向独立存活
- 后端发版不重启游戏服（玩家无感）

## 3. 通信三平面

| 平面 | 方向 | 协议 | 频率 | 语义 |
|---|---|---|---|---|
| 命令面 | Agent→Bk | REST + Idempotency-Key | 低（人操作触发） | 强一致，同步返回结果 |
| 遥测面 | Agent→Bk | `POST /telemetry/batches`（批） | 高（秒级攒批） | 至少一次 + 幂等去重 |
| 推送面 | Bk→Agent | SSE（v0 用 `GET /signals?since=` 轮询） | 低 | 指令下发（放行审批、配额变更、信号置位） |

Feign 落地方式（对"用 Feign"执念的尊重与妥协）：
- Agent 侧定义**声明式接口** `BrpServerApi`（feign 风格注解式签名），
  实现用 JDK `java.net.http.HttpClient` + MC 自带 Gson，**零第三方依赖 shade**，避开 jar-in-jar 类加载地狱。
- Backend 出站调用（Discord webhook、Mojang API）用 Spring Cloud OpenFeign（P1 引入）。

## 4. 数据主权与一致性

- **账本（货币/物品权属）唯一权威在 Backend**；游戏内展示为缓存。
- 库存快照：Agent 定期全量 + 变更增量，Backend 物化为 `inventory_snapshots`（读模型，允许陈旧，标注 `captured_at`）。
- 每条事件带：`event_id (UUID)`, `idempotency_key`, `schema_version`, `occurred_at`, `agent_id` → 重复投递安全。
- 幂等/Outbox/Saga 详见 [06-transactions](06-transactions.md)。

## 5. 降级与可用性（游戏服优先）

| 故障 | 行为 |
|---|---|
| Backend 宕机 | Agent 事件写入本地磁盘队列（append-only WAL），恢复后按序重放；游戏内只读缓存继续展示，余额标 `stale` |
| Agent 断连 | Backend 数据陈旧但一致；推送指令积压在 outbox |
| MC 服崩溃 | 磁盘队列持久化，重启续传；账本不丢 |
| 时钟漂移 | 一律以 Backend 接收时间为准做账；事件内时间仅作参考字段 |

**硬约束：Agent 永远不在 tick 线程做网络 IO**。采集在游戏线程只做无锁快照（ volatile 字段/队列 add ），发送与序列化全在独立线程池。

## 6. 安全模型

- 注册：Agent 首次启动用一次性 provisioning token 向 Backend 换取 `instance_id + secret`
- 请求：HMAC-SHA256 签名（instance_id, 时间戳, nonce, body 摘要）+ 时间窗 ±30s + nonce 去重（v0 可先 Bearer token，HMAC P1 上）
- 通道：内网 HTTP 即可，公网必须 TLS 或 wireguard
- 玩家门户：OAuth2/OIDC（游戏内发码绑定 MC 账号），权限走 RBAC（对接 LuckPerms 组同步）
- 审计：所有写操作进 `audit_log`，不可删

## 7. 技术栈清单（Backend）

Spring Boot 3.5 / Java 21 · Flyway · PostgreSQL 16 · Redis 7（缓存+pub/sub，P1） ·
Micrometer + Prometheus（Self-monitoring，吃自己狗粮） · Testcontainers + H2（测试） ·
OpenAPI 3 契约先行（brp-contract，代码生成 server stub 的选项保留）。
