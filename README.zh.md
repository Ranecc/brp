# BRP — Better Resource Plan

![enterprise readiness](https://img.shields.io/badge/enterprise%20readiness-TBD-important)
![seriousness](https://img.shields.io/badge/seriousness-0%25-red)
![emeralds lost](https://img.shields.io/badge/emeralds%20lost-0-brightgreen)
![compatibility](https://img.shields.io/badge/compatibility-best%20effort-orange)
![CI](https://github.com/Ranecc/brp/actions/workflows/ci.yml/badge.svg) <!-- the only real one -->


![build](https://img.shields.io/badge/build-maybe%20(somehow)-success)
![coverage](https://img.shields.io/badge/coverage-0%25%20(trust%20me%20bro)-yellow)
![code%20style](https://img.shields.io/badge/code%20style-AI%20hallucinated-blueviolet)
![dependencies](https://img.shields.io/badge/dependencies-Eventually%20Consistent-orange)
![PRs](https://img.shields.io/badge/PRs-welcome%20(but%20will%20break%20everything)-brightgreen)


> 为 Minecraft 交付原汁原味、企业级福报的 Spring Boot 体验 XD
>
> 非常荣幸向你介绍：SaaS 多租户玩家公会、公会 SCM 与 EHR、order-to-cash 与协议托管、
> 原版生电 / mod 产电 MES/APS、物资 WMS、全服金融市场与财务统筹、
> 服务器 TPS 交易市场 (๑•̀ㅂ•́)و✧
>
> 英文版为主文档：[README.md](README.md) · AI 动手前先读 [AGENTS.md](AGENTS.md)，并坐和放宽。

## 这是什么

| 组件 | 形态 | 技术 | 职责 |
|---|---|---|---|
| **brp-backend** | 独立 Java 服务——**不是 mod**，永不接触游戏 JVM | Spring Boot 3.5 / Java 21 / PostgreSQL | 账本、租户/RBAC、WMS/MRP/APS/EHR/SCM、经济政策、异常检测、Web API |
| **brp-mod** | **单个通用 jar**（NeoForge 1.21.1），客户端与服务器装同一个文件 | Java + Gson（feign 风格声明式客户端，JDK HttpClient 实现） | 装在**专用服务器**上 = **Agent**（采集/探针/执行/磁盘队列）；装在**玩家客户端**上 = **UI 运行时**（只读展示，写入经代理） |

> **部署通告（致新租户）**：`mods/` 目录仅放行 `brp-mod`。`brp-backend` 为独立 Java 服务，并非 Minecraft 模组；将其投入 `mods/` 不会开通任何服务，只会收到 NeoForge 出具的「这不是有效模组」认定。概不退款。

Backend 可作为游戏服务器的受管 sidecar 运行，亦可无缝托管于独立服务器。部署很灵活，破产风险不灵活。

一个 jar 统领两端——服主与玩家安装同一制品；运行时行为由部署环境裁定（见 docs/00-overview.md）。

## 仓库结构

```
brp/
├── docs/          # 设计文档（英文主文档 + .zh.md 中文译本）
├── brp-contract/  # OpenAPI + 事件契约（前后端唯一真理）
├── brp-common/    # 零依赖共享件：@Idempotent/@Cached/@Atomic/@Compensable
├── brp-backend/   # Spring Boot 服务（可编译，172/172 测试全绿（另有 mod 侧 28 个单元），CI 把关）
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
- [07-tenants](docs/07-tenants.md) — 公会租户模型、native + FTB 兼容
- [08-bridge-api](docs/08-bridge-api.md) — 第三方桥接模组稳定接口
- [09-shop-floor](docs/09-shop-floor.md) — P2：车间终端（调度/产线/表单）、区块治理、产能平衡、任务板 ⭐
- [10-backlog](docs/10-backlog.md) — 评审 backlog：8 条外部提案定级，收 7.5 条，过时前提已纠正
- [12-p3-monetary](docs/12-p3-monetary.zh.md) — P3 详细设计：快照、滞回、两税、铸币/锚价/Gresham、洗钱、CPI ⭐
- [13-p4-compute-market](docs/13-p4-compute-market.zh.md) — P4 详细设计：负载平面、预测、分时电价、需求响应、信贷、TPS 债券、配额市场、车间台 ⭐
- [14-p5-bourse](docs/14-p5-bourse.zh.md) — P5 详细设计：批量拍卖交易所、挂牌铸币厂、复本位闭环、挂牌预设 ⭐
- [15-deploy](docs/15-deploy.zh.md) — compose / k8s / helm / 单 jar：后端的四种跑法，真机启动清单 ⭐
- [16-p6-local-actuator](docs/16-p6-local-actuator.zh.md) — P6：无参启动、单人本地 ERP、区块执行器（免假人票据、智能启停）⭐
- [10-business-map](docs/10-business-map.md) — ERP 缺一半的那张地图：order-to-cash 骨架、协议、工程项目；什么以字段发布、什么被否决 ⭐
- [11-kapital-checklist](docs/11-kapital-checklist.md) — 资本论季度落地复盘：三卷可执行项，完成度业内领先 ⭐

## 快速开始（后端）

```bash
cp .env.example .env            # 填真 BRP_DB_PASS（compose 强制要求）
docker compose up -d --build    # postgres + backend，端口只绑 127.0.0.1
mvn -pl brp-backend -am test    # 213/213 全绿：账本幂等、Saga、审批、配额、计费、货币、对账科、负载平面、信贷、债券、配额市场、车间台、交易所……趁跑测试去摸摸草（方块或真草均可）
```

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 仓库 + 契约 + 账本核心（幂等/复式） + Agent 骨架 | ✅ 本仓库 |
| P1 | 租户/公会、玩家同步、Saga 引擎、审批迷宫、TPS 报告（真实 mspt）、心跳、经济可观测（M/V）、货币登记制、机器槽位配额 + 区块事件、i18n 文案、outbox + Discord 接线、门户控制台（总览/审批/经济/配额四页 + 一键裁决）已铺；门户认证/RBAC、Agent 运行时接线待做 | 🚧 |
| P2 | WMS 适配器（原版→AE2→Create）+ 入库差异与调拨单 + 铸币计量事件（docs/03 §10）+ 仪表盘 + 告警 | ✅ |
| P3 | 货币机器：每周快照、累进两税、体制滞回、铸币窗口、锚价 + Gresham、洗钱检测、CPI（docs/12） | ✅ |
| P4 | 负载台：负载遥测 + 预测、分时电价、需求响应、信贷台（债务）、TPS 债券、配额市场、车间 OEE、CC:T 状态（docs/13） | ✅ |
| P5 | 交易所：批量拍卖、挂牌铸币厂、复本位闭环、挂牌预设、模组物品目录与仓库（docs/14）；部署：k8s + helm chart + 单 jar sidecar（H2 文件兜底）（docs/15）。仍排队：跨维度市场准入、逆周期合并操作手册（docs/11 卷三） | 🚧 |
| ∞ | 剥夺者就要被剥夺了（长尾风险——外部、不可控、未批预算） | 🔮 无法排期（docs/11） |

## 许可证

致力于为您打造一个干净的，*“企业级”* 的环境：

| 模块 | 许可证 | 理由 |
|---|---|---|
| `brp-mod` | MIT | 玩家和整合包作者值得零门槛 |
| `brp-common` | MIT | 两边共用，保持 mod jar 干净 |
| `brp-backend` | Apache-2.0 | *企业级*仪式：显式专利授权、NOTICE 文件，全套仪式感 |
| `brp-contract` | Apache-2.0 | 请寻找法务部盖章 |

### 法律食物链

1. **Mojang EULA**——这个世界的最高法，不可谈判，自带光源。我们的许可证不覆盖它、不干扰它；老实说，我们不能确定它知道本仓库的存在。
2. 你所在服务器的规则——地方法规，因辖区（即服务器）而异。
3. **我们的 MIT / Apache-2.0**——一盏台灯。只管辖我们自己的原创代码。Minecraft 本体仍是 Mojang 的专有财产；我们不主张其代码库的所有权，也没有许可它的权利（见第 1 条，太阳）。

万一 Backend 真的是个好ERP呢？

## 免责声明

请注意：本 Backend 是专门面向 Minecraft 玩家的资源计划与管理系统。其底层数据模型与真实的现代企业业务模型完全脱节。因此，我们强烈不建议您将其用于任何真实的生产经营环境，否则您的公司大概率会在第二个游戏刻面临破产清算。对于任何因尝试在现实中复刻本系统而导致的财务造假、资产流失或公司倒闭，本项目组不承担任何连带责任。
但是，如果您真的想在 Minecraft 服务器里合法合规地经营一家企业，我们非常欢迎并可能接受相关的 Issue。

## 鸣谢

非常感谢我的 **GLM 狐狸** 、**大肥鱼** 和 **Qwen 小兔** （自家养的）。
是它们把我从 CRUD 的屎山里解救出来——
然后让我有能力造一个更大的 CRUD 八股文屎山。
