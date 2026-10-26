# BRP — Better Resource Plan

> 给 Minecraft 服务器的一套"企业级"资源计划系统（ERP for Minecraft）。  
> 公会是租户，红石农场是产能，管理方块是探针，账本是唯一真理。  
> 英文版为主文档：[README.md](README.md)

## 这是什么

| 组件 | 形态 | 技术 | 职责 |
|---|---|---|---|
| **brp-backend** | 独立进程，不进游戏 JVM | Spring Boot 3.5 / Java 21 / PostgreSQL | 账本、租户/RBAC、WMS/MRP/APS、经济政策、异常检测、Web API |
| **brp-mod** | **单个通用 jar**（NeoForge 1.21.1），客户端与服务器装同一个文件 | Java + Gson（feign 风格声明式客户端，JDK HttpClient 实现） | 装在**专用服务器**上 = **Agent**（采集/探针/执行/磁盘队列）；装在**玩家客户端**上 = **UI 运行时**（只读展示，写入经代理） |

一个模组文件走天下——服主和玩家装同一个 jar，跑什么由运行环境（dist）决定（见 docs/00-overview.md）。

## 版本决策（2026-10 调研结论）

- **主战场：Minecraft 1.21.1 + NeoForge**。Create 6 与 AE2 均官方支持 1.21.1，
  2026 年 9 月更新的整合包（如 All Of NeoForge）仍以 1.21.1 为基准；科技 mod 生态最深。
- MC 已于 2026 年切换年份版本号（26.1 ≈ 旧 1.22，26.2 为第二次 drop，NeoForge 模板已支持到 26.3）。
  26.x 是前瞻目标，等 Create 官方登陆后再做移植评估。
- Create 官方仅 NeoForge → **加载器锁定 NeoForge**，不做 Forge/Fabric 双出（见 docs/02 论证）。

## 仓库结构

```
brp/
├── docs/          # 设计文档（英文主文档 + .zh.md 中文译本）
├── brp-contract/  # OpenAPI + 事件契约（前后端唯一真理）
├── brp-common/    # 零依赖共享件：@Idempotent/@Cached/@Atomic/@Compensable
├── brp-backend/   # Spring Boot 服务（可编译，10/10 测试绿）
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
mvn -pl brp-backend -am test    # 14/14：账本幂等、Saga、公会……
```

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 仓库 + 契约 + 账本核心（幂等/复式） + Agent 骨架 | ✅ 本仓库 |
| P1 | 租户/RBAC、玩家档案、Web 门户、Agent 上报链路打通 | ⬜ |
| P2 | WMS 适配器（原版→AE2→Create）+ 仪表盘 + 告警 | ⬜ |
| P3 | MRP（BOM 展开 + 工单） | ⬜ |
| P4 | APS + 机器监控 + 信号中枢 | ⬜ |
| P5 | 交易所/期货、k8s + helm chart（行为艺术收尾） | ⬜ |
