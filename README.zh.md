# BRP — Better Resource Plan

![enterprise readiness](https://img.shields.io/badge/enterprise%20readiness-TBD-important)
![seriousness](https://img.shields.io/badge/seriousness-0%25-red)
![emeralds lost](https://img.shields.io/badge/emeralds%20lost-0-brightgreen)
![CI](https://github.com/Ranecc/brp/actions/workflows/ci.yml/badge.svg) <!-- the only real one -->

```shields.io
![build](https://img.shields.io/badge/build-maybe%20(somehow)-success)
![coverage](https://img.shields.io/badge/coverage-0%25%20(trust%20me%20bro)-yellow)
![code%20style](https://img.shields.io/badge/code%20style-AI%20hallucinated-blueviolet)
![dependencies](https://img.shields.io/badge/dependencies-Eventually%20Consistent-orange)
![PRs](https://img.shields.io/badge/PRs-welcome%20(but%20will%20break%20everything)-brightgreen)
```

> 为 Minecraft 交付原汁原味、企业级福报的 Spring Boot 体验 XD
>
> 非常荣幸向你介绍：SaaS 多租户玩家公会、公会 SCM 与 EHR、
> 原版生电 / mod 产电 MES/APS、物资 WMS、全服金融市场与财务统筹、
> 服务器 TPS 交易市场 (๑•̀ㅂ•́)و✧
>
> 英文版为主文档：[README.md](README.md) · AI 动手前先读 [AGENTS.md](AGENTS.md)，并坐和放宽。

## 这是什么

| 组件 | 形态 | 技术 | 职责 |
|---|---|---|---|
| **brp-backend** | 独立进程，不进游戏 JVM | Spring Boot 3.5 / Java 21 / PostgreSQL | 账本、租户/RBAC、WMS/MRP/APS/EHR/SCM、经济政策、异常检测、Web API |
| **brp-mod** | **单个通用 jar**（NeoForge 1.21.1），客户端与服务器装同一个文件 | Java + Gson（feign 风格声明式客户端，JDK HttpClient 实现） | 装在**专用服务器**上 = **Agent**（采集/探针/执行/磁盘队列）；装在**玩家客户端**上 = **UI 运行时**（只读展示，写入经代理） |

一个模组文件走天下——服主和玩家装同一个 jar，跑什么由运行环境（dist）决定（见 docs/00-overview.md）。

## 仓库结构

```
brp/
├── docs/          # 设计文档（英文主文档 + .zh.md 中文译本）
├── brp-contract/  # OpenAPI + 事件契约（前后端唯一真理）
├── brp-common/    # 零依赖共享件：@Idempotent/@Cached/@Atomic/@Compensable
├── brp-backend/   # Spring Boot 服务（可编译，测试由 CI 把关）
└── brp-mod/       # 单 jar 通用模组：防腐层端口、sidecar 进程管理、
                   # 适配器 SPI、WAL 队列、SQLite/Caffeine 内嵌方案
```

部署：Backend 以**受管 sidecar** 运行（Agent 把 `brp/brp-backend.jar` 当子进程
拉起/重启/随服停止），或 docker-compose 独立部署——同一个 jar（docs/01 §2.5）。

## 设计文档索引

- [00-overview](docs/00-overview.md) — 愿景、术语、边界
- [01-architecture](docs/01-architecture.md) — 架构、版本决策、通信平面、降级、安全
- [02-integrations](docs/02-integrations.md) — AE2 / Create 6 / CC:T / 无线红石 调研与适配设计 ⭐
- [03-economy](docs/03-economy.md) — 复式账本、CPI、货币政策、危机检测、TPS 商品化（笑）
- [04-technical-control](docs/04-technical-control.md) — 生电计量与控制（流量计/信号/配额）
- [05-anomaly-anticheat](docs/05-anomaly-anticheat.md) — 行为数据驱动的异常检测 / 反作弊
- [06-transactions](docs/06-transactions.md) — 幂等、Outbox、Saga、账本并发 ⭐

## 快速开始（后端）

```bash
cp .env.example .env            # 填真 BRP_DB_PASS（compose 强制要求）
docker compose up -d --build    # postgres + backend，端口只绑 127.0.0.1
mvn -pl brp-backend -am test    # 14/14 全绿：账本幂等、Saga、公会……趁跑测试去摸摸草（方块或真草均可）
```

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 仓库 + 契约 + 账本核心（幂等/复式） + Agent 骨架 | ✅ 本仓库 |
| P1 | 租户/公会、玩家同步、Saga 引擎、审批迷宫、TPS 报告（真实 mspt）、心跳、经济可观测（M/V）、货币登记制、机器槽位配额 + 区块事件已铺；门户、Agent 上报链路待做 | 🚧 |
| P2 | WMS 适配器（原版→AE2→Create）+ 仪表盘 + 告警 | ⬜ |
| P3 | MRP（BOM 展开 + 工单） | ⬜ |
| P4 | APS + 机器监控 + 信号中枢 | ⬜ |
| P5 | 交易所/期货、k8s + helm chart（行为艺术收尾） | ⬜ |

## 许可证

致力于为您打造一个干净的，*“企业级”* 的环境：

| 模块 | 许可证 | 理由 |
|---|---|---|
| `brp-mod` | MIT | 玩家和整合包作者值得零门槛 |
| `brp-common` | MIT | 两边共用，保持 mod jar 干净 |
| `brp-backend` | Apache-2.0 | *企业级*仪式感：显式专利授权、NOTICE 文件，全套仪式感 |
| `brp-contract` | Apache-2.0 | 请寻找法务部盖章 |



万一 Backend 真的是个好ERP呢？

## 免责声明

请注意：本 Backend 是专门面向 Minecraft 玩家的资源计划与管理系统。其底层数据模型与真实的现代企业业务模型完全脱节。因此，我们强烈不建议您将其用于任何真实的生产经营环境，否则您的公司大概率会在第二个游戏刻面临破产清算。对于任何因尝试在现实中复刻本系统而导致的财务造假、资产流失或公司倒闭，本项目组不承担任何连带责任。
但是，如果您真的想在 Minecraft 服务器里合法合规地经营一家企业，我们非常欢迎并可能接受相关的 Issue。

## 鸣谢

非常感谢我的 **GLM 狐狸** 、**大肥鱼** 和 **Qwen 小兔** （自家养的）。
是它们把我从 CRUD 的屎山里解救出来——
然后让我有能力造一个更大的 CRUD 八股文屎山。
