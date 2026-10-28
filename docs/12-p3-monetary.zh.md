# 12 — P3 详细设计：货币机器

> 英文主文档：[12-p3-monetary.md](12-p3-monetary.md)
>
> 本文一切机制锚定已批准的 docs/03 §9。凡实现代理与文档理想措辞有出入，
> 偏差就地明说——文档是正典，代理保持诚实。

## 1. 范围

P3 = 检测 → 诊断 → 处方（docs/03 §9）：共享周快照、滞回机制档、两把税
（边际算力课、财富税）、BRP-09xx 告警族、铸币窗口 + 金库、锚价与重定锚
投票、Gresham 检测器，外加两条已定级的 backlog 收录（R-4 洗钱环路、
R-6 CPI 数据面）。

**非目标**（不变）：期权永久封印；处置止步 NOTIFY，人类掌扳机；无真实
货币；不开第三条治理通道——政策投票走既有审批阶梯。

## 2. 数据模型（V15–V17，每个任务切片一条迁移）

```sql
-- V15：共享快照 + 机制档 + 两本税账
weekly_snapshots(week_of DATE PK, gini DOUBLE, top_share DOUBLE,
                 regime_after VARCHAR(8), captured_at)
snapshot_tenants(week_of DATE, tenant_id, wealth_cents BIGINT,
                 velocity DOUBLE, PRIMARY KEY (week_of, tenant_id))
policy_regimes(name VARCHAR(32) PK, state VARCHAR(8), entered_at,
               last_reading DOUBLE, updated_at)          -- 'antitrust'
levy_minutes(minute TIMESTAMP, tenant_id, share_pct DOUBLE, units DOUBLE,
             amount_cents BIGINT, posted BOOLEAN DEFAULT FALSE,
             PRIMARY KEY (minute, tenant_id))            -- 只读插入幂等
wealth_assessments(week_of DATE, tenant_id, bracket_cents BIGINT,
                   surcharge BOOLEAN, amount_cents BIGINT,
                   PRIMARY KEY (week_of, tenant_id))     -- 只读插入幂等

-- V16：锚价、金库、政策请求
peg_state(leg VARCHAR(8) PK, rate DOUBLE, band_low, band_high, updated_at)
peg_history(id, leg, old_rate, new_rate, request_id, changed_at)
vault_registrations(tenant_id, container_ref, item_id, PRIMARY KEY(...))
vault_movements(id, tenant_id, direction, item_id, qty, correlation, at)
policy_requests(id PK, kind, payload_json, status, requested_by, at)
                 -- kind: 'repeg-vote' | 'peg-band-emergency'

-- V17：价格 + 洗钱发现
price_observations(id, item_id, price_cents, currency, qty, venue, observed_at)
cpi_daily(day DATE PK, index DOUBLE, basket_json TEXT)
wash_findings(id, tenant_id, accounts_json, cycle_len INT, score DOUBLE, at)
```

Gresham 的轨道标记**无需建表**：ESCROW 分录携带 memo 前缀
`rail:emerald:` / `rail:quota:`——铸币窗口和配额兑付流写前缀，检测器读
前缀。

## 3. 共享周快照（P3-01）

- Cron `brp.snapshot.cron`（默认周一 00:05，`brp.reports.zone` 时区）。
- 每租户：`wealth_cents` = 金库余额；`velocity` = 本周名义成交额 /
  max(平均余额, 1)，成交额来自 `journal_entries` 按租户求和。
- 服务器行：对全体租户财富算 Gini（零财富租户计入分母——分母是真相，
  不是宣传口径）；`top_share` = 首富占比。
- 按 `week_of` 幂等（先插入，DVE = 已捕获）。

## 4. 滞回机制档（P3-02）

`policy_regimes['antitrust']`：每次快照后 reading = gini；
- ≥ 0.8 → ACTIVE（入场发 BRP-0901），< 0.7 → INACTIVE（BRP-0902）；
- 带间保持原状（半开断路器；来回抖的税就是玩家脸上的抽搐）。
`top_share > 0.5` 连续两周 → BRP-0950（红线；只喊人，不收钱）。
所有课税机制先问档位。档位只读端点 `GET /api/v1/monetary/regime`
（agent 与门户共用）。

## 5. 边际算力课（P3-04）

- **份额来源 v1 代理（指名偏差）**：docs/03 由流量计测舰队 mspt 份额；
  在逐机 mspt 计量落地前（P2-14 普查只有台数没有周期），份额 =
  租户区块加载分钟 / 全服分钟，与计量器和 APS 用**同一批**
  `chunk.load.minutes` 行。一个数字养全家（docs/09 §5）。代理日后换成
  mspt 份额，下游零改动。
- 分钟评估（每分钟跑上一分钟）：对有加载分钟的租户，边际档位计价：
  0-20% @1.0x | 20-35% @1.2x | 35-50% @1.6x | >50% @2.5x——按份额曲线
  分段作用于租户自己的分钟（所得税语义：永远卡 49.9% 不是策略）。
- 绿宝石计价：`金额 = 单位 × base_emeralds × 档位 × peg`——基准
  `brp.levy.compute.base-emeralds-per-minute`（默认 0 = 上膛但不收费，
  等腐竹给算力定价），按绿宝石腿锚价折 BRC 分。绿宝石是发票语言；
  账本只说 BRC。
- 账本过账：行先落 `levy_minutes`；物化器按分钟批为一租户一分录
  （`treasury` CREDIT → `expense-sink` DEBIT），幂等键
  `tax:{tenant}:{yyyyMMddHHmm}`——重放安全，绝不双收（先插入，V12 纪律）。
- 档位门控：须 ACTIVE。INACTIVE 周仍按 0 评估并标 `posted=TRUE`
  （宽限周的记录也是记录）。

## 6. 财富税（P3-03）

每周、档位门控、源自信享快照：对 `wealth_cents` 阶梯税率：
≤ 100万：0% | ≤ 1000万：0.5% | > 1000万：1.5%，`velocity < 0.5` 加囤币
附加 `brp.levy.wealth.hoarding-surcharge`（默认相对 +50%）。每
(周, 租户) 一条分录，幂等 `wealth:{tenant}:{week}`，金库 → SYSTEM_SINK。
净投放中记负数：烧掉，不是花掉。

## 7. BRP-09xx 告警族（P3-05）

| 代码 | 触发 | 级别 |
|---|---|---|
| 0901/0902 | 档位 ACTIVE/INACTIVE 翻转 | INFO/WARN |
| 0903 | Gresham 单腿占比 > 95%（7 天） | CRIT |
| 0904 | 金库漂移（实物 ≠ 累计账） | CRIT |
| 0905 | 铸币窗口补偿耗尽 | CRIT |
| 0906 | 课税物化器连续失败 > 5 | WARN |
| 0950 | 首富份额 > 50% 持续两周——红线 | CRIT |

告警落 `anomaly_hits`（rule id = 代码）+ outbox 通知；0950 文案明说：
工具不能既是税又是死刑。

## 8. 铸币窗口 + 金库（P3-06）

- 金库 = 每租户登记的容器（`vault_registrations`）；出入落
  `vault_movements`，以 correlation id 关联。
- Saga `mint-window`（用既有 SagaEngine）：第 1 步 `book-faucet` 先记
  FAUCET 分录；第 2 步 `dispense` 发 `vault.dispense` 信号指令（mod 侧
  执行器，P3-12）并等其 ack 后落账；补偿逆序（撤销发放、再冲回分录）。
  崩溃恢复走 P2 修好的 payload 反序列化——escrow 的教训保持已学状态。
- 漂移核验（每小时）：实物库存快照（agent 库存探针）对累计
  FAUCET/发放账；不平 → BRP-0904。金库不许与账本意见相左，违者吃文书。
- 超过 `brp.mint.approval-threshold` 的铸币自动提审批（走既有迷宫，
  不开新通道）。

## 9. 锚价 + 重定锚（P3-07）

- `peg_state`：两条腿（'emerald'：每组 BRC；'quota'：每配额分钟 BRC），
  带 ±5%。
- 带外读数连续两次快照 → 自动提交 `policy_requests('repeg-vote')`
  附建议汇率——OWNER 审批（同一角色阶梯）；执行原子写 `peg_state` +
  `peg_history`（带审批 id）。无第三条治理通道；RFC P3 在此同样没有
  豁免权。

## 10. Gresham 检测器（P3-08）

对 ESCROW 分录按 `rail:*` memo 前缀做 7 天滑窗聚合；单腿占比 > 95% →
BRP-0903 + 注入 docs/03 §5 危机规则表（规则即数据——它就是一条带计算
字段的 L1 热规则）。处方留给人：重定锚，或弱腿临时浮动。

## 11. 洗钱环路（P3-09，docs/10 R-4）

每租户：账户为顶点，租户内每条分录的 DEBIT/CREDIT 对为有向带权边。
Tarjan SCC；发现 = 尺寸 ≥ 2 且环长 ≤ 4 的 SCC，按整额度（金额 % 1000
== 0）、紧凑度（所有腿一小时内）、占该租户周成交额比重打分。环路本身
是合法商业——分数扛指控，指控是文书（anomaly hit）不是惩罚。每周跑 +
按需 `POST /api/v1/monetary/wash/scan`。

## 12. 价格 + CPI（P3-10，docs/10 R-6）

- 摄入：`market.price.observed` 事件（events.md 已登记）经 fanOut 钩子
  落 `price_observations`；手工录入走 `POST /api/v1/monetary/prices`
  （门户表单 + API；商店插件喂数随 P4 WMS 到场）。
- CPI 日更：对基周价格比取几何平均，篮子与权重来自 `brp.cpi.basket`
  （配置 JSON）。`cpi_daily` 喂门户曲线和 §5 危机规则（通胀/紧缩）。

## 13. Mod 侧（P3-12）

只有一件事：`vault.dispense` 信号执行器——agent 既有信号轮询执行已
登记金库容器的取出并回 ack（带 movement id）。P3 其余全是后端 + 门户。

## 14. 门户 + 契约

- 门户页 `货币 / Monetary`：档位徽章、最新快照（Gini、V、首富份额）、
  两税账单表、Gresham 轨道条形图、CPI 曲线（内联 SVG，不上 JS 框架——
  企业铬不改）、锚价卡片、政策请求队列（可批可驳）。
- OpenAPI：`/monetary/*`（regime、snapshot、levies、gresham、cpi、
  prices、wash/scan、policy-requests）；events.md 已含
  `market.price.observed`。

## 15. 配置键（默认全部「上膛但不生效」）

| 键 | 默认 | 含义 |
|---|---|---|
| brp.snapshot.cron | Mon 00:05 | 周快照 |
| brp.regime.gini.on / .off | 0.8 / 0.7 | 滞回带 |
| brp.regime.redline-share | 0.5 | BRP-0950 红线 |
| brp.levy.compute.base-emeralds-per-minute | 0 | 算力课基准价 |
| brp.levy.compute.bands | 0.2/1.0,0.35/1.2,0.5/1.6,1.0/2.5 | 边际档位 |
| brp.levy.wealth.hoarding-surcharge | 0.5 | V<0.5 附加（相对） |
| brp.peg.emerald.rate / brp.peg.quota.rate | 64 / 10 | 每组/每分钟 BRC |
| brp.peg.band | 0.05 | ±带宽 |
| brp.mint.approval-threshold | 100000 | 分 |
| brp.cpi.basket | {} | 物品→权重 |

## 16. 任务板（验收 = 测试与文档同 commit）

| # | 任务 | 切片 |
|---|---|---|
| P3-01 ✓ | V15 + 周快照 job（Gini/V/首富份额，按周幂等） | backend |
| P3-02 ✓ | 滞回档位 + BRP-0901/0902/0950 + 档位端点 | backend |
| P3-03 ✓ | 财富税引擎（阶梯、V 附加、档位门控、只插入幂等） | backend |
| P3-04 ✓ | 算力课：分钟行（行锁累计）+ 边际档位（无悬崖）+ 物化器，幂等 tax:{tenant}:{minute} | backend |
| P3-05 | BRP-09xx 接线（0950 持续份额、0906 连败）+ 文案 | backend |
| P3-06 | V16 + 铸币窗口 saga + 金库登记/流水 + 漂移（0904） | backend |
| P3-07 | 锚价状态 + 带宽 + 自动重定锚投票 + 执行 | backend |
| P3-08 | Gresham 轨道聚合 + 0903 | backend |
| P3-09 | V17 + 洗钱环路 SCC + 扫描端点 | backend |
| P3-10 | 价格观测摄入 + CPI 日更 | backend |
| P3-11 | 门户 Monetary 页（徽章/表格/轨道/CPI/锚价/投票） | portal |
| P3-12 | mod：vault.dispense 信号执行器 | mod |
| P3-13 | 契约同步（openapi /monetary/*） | contract |
| P3-14 | 文档重同步 + 计数（03/09/12 互链） | docs |
