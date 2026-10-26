# 07 — 租户模型：公会特化、Native 与兼容双模式

> 英文版为主文档：[07-tenants.md](07-tenants.md)
>
> 调研于 2026-10。租户 = 团队/公会；模型为玩家公会特化（金库/配额/共享仓库），
> 不是通用组织架构。

## 1. 角色与形态

- 角色：`OWNER`（1 名，金库控制、解散）· `OFFICER`（邀请、按策略审批领料）· `MEMBER`
- native 建队 = 一个原子幂等操作：团队行 + OWNER 成员 + **金库账本账户**
  （`TEAM` 类型，租户=teamId）
- 玩家可属 N 个队；每队对应：金库账户、配额桶（03 §6）、仓库范围（AE2 cell 绑定，02 §2）

## 2. Native vs 兼容模式（同时只激活一个）

| | NATIVE | COMPAT（如 FTBTEAMS） |
|---|---|---|
| 权威 | BRP 后端 | 游戏侧 mod |
| 建/邀/踢 | API（Web 门户 + 终端） | 在对方 mod 里，照旧 |
| 花名册流向 | 后端 → 游戏（P1 UI） | Agent 拉取 → `POST /teams/sync`（只读镜像） |
| Native 端点 | 启用 | **拒绝改写兼容来源的行**（已有测试） |
| 队伍 ID | 自选 | 来源 mod 的稳定 UUID |

同步协议：整批替换、`batchId` 幂等、批内原子；花名册替换保留老成员
`joined_at`，退队即删。

## 3. FTB Teams 兼容（已调研）

- FTB Teams 是事实标准团队 mod（CurseForge 224M+ 下载），1.21.1 NeoForge
  活跃维护；API 经 `maven.ftb.dev.org`（`dev.ftb:ftb-teams-neoforge`）引入
- 入口（◆ 接线时核对）：`FTBTeamsAPI.api().getManager()` →
  `getTeamByID/getTeamByPlayer` → `PartyTeam.getMembers()` → 成员 uuid/角色；
  组队 API 近期变更过（changelog）——以源码为准，别信旧教程
- 第三方集成先例：Better Party X FTB Teams（Modrinth）就是同步
  成员/名字/队长，与我们适配器同一形态
- 映射：FTB party UUID = 后端 team id；owner/officer→OFFICER；单人
  player-team 跳过；兼容模式下 Agent **绝不**反向改 FTB 状态

## 4. 为什么不把 LuckPerms 组当队伍？

LuckPerms = 权限模型（运维关心），FTB/BRP = 社交单元（持久、跨功能）。
LP 组同步进 RBAC（01 §6），不进租户——混这两样正是"企业级"名声的由来。

## 5. 认证边界（用户决策：丢给 MC server）

BRP **不自建玩家认证**：游戏内身份 = MC 服务器会话（在线模式验证；Agent
只为已认证的 `ServerPlayer` 行动）。Web 门户用游戏内发码绑定（短时效，
玩家必须在线）——游戏服是唯一身份权威，BRP 是依赖方。Agent↔Backend 用
实例注册（P1 上 HMAC），那是机器认证，不是玩家认证。

## 6. 审批迷宫（已上线：后端 + 门户）

金库提现流程端到端已通——REST 在 `/api/v1/approvals`，门户审批页带
一键裁决动作：

- OFFICER 在策略内审批；金额达到/超过"高层管理"阈值进入
  `AWAITING_UPPER_MANAGEMENT`，等 OWNER 签字（配置：
  `brp.approval.rubber-stamp-cents` / `upper-management-cents`）
- 橡皮图章阈值内自动批准——官僚体系高效到跳过自己，备忘归档，KPI 达成
- 每次操作追加一条 `approval_action`（SUBMITTED / RUBBER_STAMPED /
  APPROVED / REJECTED / NUDGED）：留痕本身就是产品
- per-my-last-email 催办（`POST /requests/{id}/nudge`）记录升格表演并
  返回一个（就一个）黑话词；投递走 outbox
- 执行跑 `vault-withdraw` saga（冻结 → 发放 → 结算，反向补偿），幂等键由
  requestId 派生——梗给人看，不变量给数据库看

## 来源

- FTB Teams (NeoForge): https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge
- FTB-Teams 源码/changelog: https://github.com/FTBTeam/FTB-Teams/blob/main/CHANGELOG.md
- Better Party X FTB Teams（集成先例）: https://modrinth.com/project/WFWJ2H8D
- FTB Teams 文档: https://docs.feed-the-beast.com/mod-docs/mods/suite/Teams
