# P4 — 负载台：算力市场、需求响应、信贷、债券

> 状态：**设计定稿，实现进行中** · 阶段：P4
> 前置：docs/12（P3 货币机器）· 后继：P5（交易所，讽刺天花板）
>
> P3 回答了*谁来为 tick 买单*。P4 回答*tick 什么时候被用掉*。服务器有昼夜
> 脉搏——晚上九点满载，清晨六点闲置——而每个强制加载区块不分时 收一样的钱。
> 这就是 P4 要修的定价错误，用的正是每个电网调度中心迟早都要掏出来的三件
> 工具：分时电价、需求响应、债务。外加讽刺皇冠上的明珠：票息按**实际交付的
> tick** 结算的债券。

## 1. 实地调研：MC 服务器到底把 tick 花在哪

以下全部来自 minecraft.wiki（`/w/Tick`、`/w/Chunk_loading`、
`/w/Mob_spawning`），2026 年 10 月阅读，每一条都对应 §2–§5 的一个科目。
没有传说，只有 wiki。

| Wiki 事实 | 数字 | 经济学后果 |
|---|---|---|
| Tick 预算 | 50 ms = 20 TPS；MSPT 是实际成本 | MSPT/50 就是 [0,∞) 上的**产能利用率**；P4 全部定价装置都挂在它上面 |
| 强制加载区块票 | 等级 31，实体 tick，**无超时**，跨存档持久 | 强制区块是一份*永久*算力债权——正是 P2 managed_chunks 在计费的东西。原版从不回收它，所以计费必须为"永久"定价 |
| 维度空闲超时 | 300 tick（15 秒）无活动即停实体 tick | 原版早就把闲置定价为零。BRP 区块费是它的镜像：玩家付钱保留原版本要丢掉的东西 |
| 全局刷怪上限 | `mobCap × 区块数 ÷ 289`，满额敌对 70；多玩家共享区块只算一次 | 把玩家摊开在全地图会**抬高**上限 → 更多实体 → 更多 MSPT。教科书级公地悲剧，wiki 原文印发 |
| 单玩家刷怪范围 | 刷怪需 128 格内有玩家；超 128 格立即消失 | 玩家在场是实体负载的稀缺触发器——所以 census 要数实体，不能只数区块 |
| 随机 tick | 每 16³ 区段每次区块 tick 随机 3 个方块（均摊 68.27 秒/方块）；`randomTickSpeed` 是乘数 | 农作物区块便宜；刷怪塔和红石区块不便宜。统一单价会高估便宜的那批——电价乘数来修 |
| 计划 tick 天花板 | 每 game tick 65,536 个（方块先、流体后） | 批量农场能在晚上九点、所有人刚上线时把天花板撞响——这就是"峰值"需求响应存在的原因 |
| 自动存档 | 每 6,000 tick（5 分钟）一次 | 周期性 MSPT 尖峰、周期已知；预测器的 EWMA 不能追着它跑（§3 平滑） |
| 漏斗 | 持续扫描头顶掉落物；盖住即停 | 榜上有名的空闲耗电；OEE 的"堵塞/闲置"行（§6）从此是实测数，不是氛围 |
| 方块实体 | 在实体之后 tick，每个实体 tick 区块都有 | P2-14 census 按命名空间数它们——这个计数是负载模型最好的预测变量，现在随心跳上行 |

从表格里提炼的设计规则：**玩家就是负载发生器**。票来自玩家、刷怪上限随
玩家散布缩放、需求响应存在的原因是玩家按作息上线。所以负载平面每分钟
*同时*采样玩家、实体、区块——少了任何一个，预测器都没有意义。

## 2. 负载遥测平面（slice A）

**V19 `load_samples`** —— 每 UTC 分钟一行：

```
load_samples(bucket_minute PK, sample_count, mspt_avg, mspt_max,
             player_max, entity_max, chunk_max, forced_max)
```

心跳本来就在流 `mspt/tps/backlog`（P1 接线）；census 阶段现在补上
`players`、`entities`、`chunks`、`forced`（取自 agent 已注册集合——不新增
游戏线程 IO；`getPlayerCount()`、逐维度实体计数、`getLoadedChunksCount()`）。
`TelemetryServiceImpl` 对分钟桶做 upsert：mspt_avg 用滚动平均，其余取 max。

**`load_profile`（V19）** —— 可持久的分钟级日周期：
`minute_of_day PK, ewma_mspt, sample_days`。每天 00:1x 由定时任务刷新：
每分钟 `ewma = 0.95·ewma + 0.05·today`（α = 0.05，`brp.load.profile-alpha`）。
冷启动（sample_days < 3）时预测器如实报告"样本不足"而不是编数字——与
Gresham 空基线保持沉默是同一条诚实规则。

**`LoadForecastService`** —— `forecast(hours)` 返回未来 N 个小时桶：

```
predicted(m) = clamp01utilization( profile(m) + 0.5 · (live − profile(now)) )
```

- `live` = mspt_avg 的 30 分钟 EWMA（α = 0.3，`brp.load.live-alpha`）；
- 偏离项阻尼 0.5（`brp.load.live-damp`）——日周期是先验，当下是修正，
  自动存档尖峰按定义是暂态；
- 输出同时按电价表（§4）给每小时标注 **VALLEY / SHOULDER / PEAK**，并给出
  `schedulable` = 预测 MSPT ≤ 预算（§5）。

`GET /api/v1/monetary/load/forecast` 与 `.../load/samples?hours=` 开放。
门户画曲线（monetary.html 的 CPI SVG 同款画法）。

## 3. 利用率与 DR 预算

`brp.dr.mspt-budget`（默认 **40**，预算 50 ms）：超过它，调度器停止授予
新窗口、运行中的窗口被切除（§5）、预测把对应小时标为不可调度。利用率本身
随预测响应发布：`utilization = mspt / 50`。P3 的 0950 红线保持最高主权——
呼叫值班是监管台的事；DR 是市场的事。一个衡量伤害，一个给伤害定价。

## 4. 分时电价（slice B）

**V19 `tariff_windows`** —— 版本化的 TOU 表：

```
tariff_windows(id PK, version, day_of_week -1..6 (-1=每天), start_minute,
               end_minute, multiplier_pct, label)   -- label: VALLEY/SHOULDER/PEAK
```

`TariffService.multiplierAt(dateTime)` 取版本最高、命中的行，缺省 100%。
种子数据直接来自 chunk 计费现有的配置键
（`brp.chunk.billing.peak-start-hour 19 / peak-end-hour 23 /
peak-surcharge-percent` → version 0 的一条 PEAK 窗口）。随后
ChunkServiceImpl **改为查电价表而不是自己的私有字段**——R-3b 峰时加价成为
通用电价表的第一个客户，单一事实来源，PeakPricingTest 保持绿并新增
表驱动用例。乘数允许小于 100%（谷段 `multiplier_pct 60`）——平峰填谷从
此字面意义上就是表里的一行。

全系统计费恒等式：`price = base × multiplier(解析分钟)`，发票行记录
`tariff_version` 与解析出的乘数。不搞追溯重定价——落账时点盖章版本，
跟每家电力公司的服务条款脚注一个做法。

## 5. 需求响应 —— 平峰填谷（slice B）

**V19 `deferred_jobs`**：

```
deferred_jobs(id PK, tenant_id, idem_key UNIQUE, resource, quantity,
              est_minutes, earliest, deadline, status, awarded_start,
              awarded_minutes, shed_count, created_at)
status: FILED → AWARDED → RUNNING → DONE
                    ↘ SHED（回到 AWARDED，期限顺延）
```

**`DemandResponseService`**：

- **申报**（`POST /tenants/{id}/deferred-jobs`，幂等 `dr:{tenant}:{key}`）：
  租户申报可推迟的工作——"周六前把刷怪塔开 90 分钟，什么时候都行"。
  `est_minutes × base-emeralds-per-minute`（与算力税同一个
  `brp.levy.compute.base-emeralds-per-minute`——算力只有一种价格）是成本基数。
- **授予**（调度 tick，`brp.dr.award-every`）：对每个 FILED 作业，候选窗口 =
  `deadline` 之前预测 `schedulable` 的小时；按 `(电价乘数, 预测 mspt)` 升序
  排序——最便宜的先来，这就是*填谷*；贪心授予连续分钟，并避开同一租户
  已授予的窗口。授予时按窗口乘数计一笔**预约费**（幂等
  `dr-reserve:{jobId}:{windowSeq}`）；预约费就是收入。到 deadline 仍未授予
  = 作业 FILED→EXPIRED，不收费——"承诺安排"不是账单。
- **下发**：已授予窗口的 OPEN/CLOSE 走现有信号平面（`dr.window` 事件，像
  所有信号一样 ack；按 jobId+seq 幂等）。mod 的 SignalDesk 路由给 governor
  feed：被标记的产线只有在自己的窗口内才允许开机。窗外 governor 压线——
  机器想无视电价都开不起来。
- **切除**：窗口运行中实时 mspt > 预算 → 运行中的 OPEN 窗口提前 CLOSE
  （DR *最先*被切——它按定义就是可中断负荷；P2 的欠费区块切断比 DR 更硬，
  信贷熔断比欠费切断更硬），`shed_count++`，期限 += 切除时长，窗口重新授予。
  超过 `brp.dr.max-sheds`（2）次则作业取消、预约费退款。切除事件进停机台账
  （§7）记 `DR_SHED`——OEE 看得见电网调度员的手。

平峰填谷一句话版：谷段乘数让凌晨六点变成最便宜的小时，调度器把每一座
可推迟的农场赶进这些小时里，晚上九点的计划 tick 天花板从此不再被刷怪塔
堵门。

## 6. 信贷与债务（slice C）

**V19 `credit_lines` / `credit_events`**：

```
credit_lines(tenant PK, limit_cents, drawn_cents, accrued_interest_cents,
             status, status_since, annual_rate_pct, updated_at)
  status: GOOD → WATCH（利用率 ≥80%）→ RESTRICTED（100%）→ DEFAULTED（宽限期出）
credit_events(id PK, tenant, kind, amount_cents, memo, created_at)
  kind: DRAW|REPAY|INTEREST|LIMIT_SET|STATUS
```

- **额度**：管理员设定，或自动 = `limit-factor × 近 30 天 BRC 收入`每周重算
  （下限 `brp.credit.limit-floor-cents`）。债务以 BRC 计——费用是 BRC，
  平滑区块费尖峰需要的是 BRC 授信，不是配额。
- **提款**：隐式。当一笔记账会让*现金*账户透支时，账本拒绝（P0 不变量——
  不许静默铸币）；**付款方**此时可用信贷结算：提款分录
  `credit-draw:{txn}`（SYSTEM 出借人腿），现金方完成支付。提款 ≤ 额度，
  否则 402 `credit.limit.exceeded`。账本的零铸币不变量完好：信贷转移的是
  存量 BRC，从不创造。
- **利息**：每日计提任务 `brp.credit.accrual-cron`：`drawn ×
  annual_rate/365`（`brp.credit.annual-rate-percent` 12）——单利不复利；
  复利是会在 Minecraft 里被通缉的行为。利息并入 `drawn`。
- **状态机**：利用率 80% 时 GOOD → WATCH（提示性，门户徽章）→ 100% 时
  RESTRICTED → RESTRICTED 持续 `grace-days`（7）天且无净还款 → DEFAULTED。
  恢复：提款降到阈值以下降一级（RESTRICTED → WATCH → GOOD）；DEFAULTED
  需要管理员 `REINSTATE`——债务减免在这里也是董事会决定。
- **熔断**（RESTRICTED+）：P2 的承诺不破——*移动和游玩保持免费*。停下的
  是：（a）全部受管区块走管理员远程关闭路径自动断电（区块就是抵押品，
  字面意义），（b）DR 申报与运行窗口挂起（运行中的按 §5 切除），（c）
  市场下单冻结（§8c），（d）债券：已持有的继续计息——债权的清白不因
  债务人受罚，这正是有限责任的全部意义。每个熔断动作都是一条
  `credit_events`：有审计痕迹才算发生过。

## 7. TPS 债券（slice D，R-7 从 P5 提前）

**V20 `bond_series` / `bond_subscriptions` / `bond_accruals`**：

```
bond_series(id PK, name, face_cents, coupon_pct_annual, term_days,
            total_units, sold_units, threshold_mspt, status, issued_at,
            matures_at)
  status: OPEN → AWARDED(足额) → MATURED → REDEEMED / FAILED(认购<60% → 全额退款)
bond_subscriptions(id PK, series_id, tenant, units, filled_cents,
                   coupon_paid_cents, principal_paid_cents)
bond_accruals(series_id, tenant, day, amount_cents, PK(series,tenant,day))
```

机制刻意乏味：

- **发行**（管理员）：财政部公布规模、面值、票息、期限，以及表现阈值
  `brp.bond.threshold-mspt`（默认 **45**）。
- **认购**（租户，OPEN 期间）：BRC 从租户转入财政部托管账户（幂等
  `bond-sub:{series}:{tenant}`）。开放期 +`ttl` 后认购不足 → 全额退款，
  状态 FAILED——债券失败也要失败得体面。
- **票息计提**：每日 `face × coupon/365` 入 `bond_accruals`，与信贷利息
  同一套定时纪律、同一个可注入时钟的测试套路。
- **到期结算**：`avg_mspt` 取 [发行, 到期] 区间的 `load_samples`（slice A
  是硬依赖——债券按*实测* tick 结算，这就是整个笑话的题眼）：

  ```
  performanceFactor = clamp(threshold / avg_mspt, 0.5, 1.0)
  coupon_paid = accrued × performanceFactor
  ```

  全程低温运行（均值 30 ms）→ 因子封顶 1.0，足额票息。全程高温（90 ms，
  预算两倍）→ 因子 0.5：财政部**用它实际拥有的 tick 支付**。本金永远足额
  兑付——违约不是讽刺素材，违约是 P5 的问题，而且我们打算永不违约。
  支付幂等：`bond-coupon:{series}:{tenant}`、`bond-principal:{series}:{tenant}`。
  债券免手续费，什么也不豁免，期权永久封存（docs/00 非目标）——期货是
  路线图批准的讽刺；衍生品的衍生品是服务器暴毙的方式。

## 8. 配额市场（slice E）

工业品轨道终于有了二级市场。有多余算力分钟的生产者报价，有峰值农场要
开工的消费者接货。锚（peg 的 quota 腿，10.0 BRC，±5% 带——V16）是市场
围绕振荡的锚点，财政部的手续费账本终于有了第二行。

**V20 `market_orders` / `market_trades`**：

```
market_orders(id PK, tenant, idem_key UNIQUE, side ASK|BID,
              qty, qty_remaining, price_cents, status, created_at, expires_at)
  status: NEW → PARTIAL → FILLED / CANCELLED / EXPIRED
market_trades(id PK, ask_id, bid_id, qty, price_cents, fee_cents, created_at)
```

- **挂单**（`POST /api/v1/market/orders`，幂等 `ord:{tenant}:{key}`）：价格
  必须落在锚带内（`|price − anchor| ≤ band × anchor`，取 `peg_state`）——
  市场交易的是算力，不是狙击锚定的赌注；那是重锚台（repeg desk）的辖区。
  RESTRICTED 租户禁止下单（§6c）。
- **撮合**：每次挂单后，taker 按**价格-时间优先**扫单（`price, created_at,
  id`——每家真实交易所从纸质时代继承下来的排序）。两张订单行在同一事务里
  `findForUpdate` 加锁；成交按逐笔幂等 `fill:{askId}:{bidId}:{seq}` 记录。
- **结算**（同一事务）：BRC 分录 买方 → 卖方（`price × qty` 分，memo
  `rail:quota`——Gresham 台看得见工业量能增长），配额交割 = 双方租户的
  `compute` 桶增减，财政部手续费 `brp.market.fee-bips`（50 基点）入 sink。
- **自成交禁令**：撮合候选两侧是同一租户 → 跳过该候选（不给洗钱检测器
  送票——R-4 可以继续无聊下去）。
- **生命周期**：TTL `brp.market.order-ttl-days`（7）到期作废任务；挂单人
  撤单；部分成交留 `qty_remaining`。不做保证金，不做卖空——手里没有的
  分钟不许卖（结算时桶增量查 ≥ 0）。

## 9. OEE、成本、资产（slice F）

docs/10-business-map 给 P4 立下的承诺，收小、贴数据：

- **停机台账（V21 `downtime_events`）**：`tenant, machine/line, reason
  枚举（CHUNK_UNLOADED | HOPPER_JAM | NO_POWER | MANUAL | DR_SHED |
  CREDIT_CUTOFF), since, until, minutes`。来源是 agent 已经会说的遥测
  事件，加上 §5/§6 的机器动作。**OEE = 可用率 × 表现率**（按租户周）；
  质量率在出现返工数据之前保持 1.0——A×P 是实测，Q=1 是*披露*，脚注写明。
- **工单成本**：完工时把 `material_cost_cents`（Σ 领料）+
  `machine_cost_cents`（机时 × 电价解析基数）盖到工单上——业务地图里 CO
  的建议，现在定表，P4 报表免费拿。
- **资产登记（V21 `assets`）**：项目里程碑移交即登记
  `{id, tenant, kind, label, location_chunk, cost_basis_cents, acquired_at,
  status}`。直线折旧**刻意推迟**——登记簿只存成本基础；P5 想要 Minecraft
  MACRS 的话，请像其他人一样走提案流程。

## 10. CC:T 适配器（slice G，R-5 提前）

电脑（CC:T）不需要 mod 依赖——需要 HTTP 和一个 Lua 文件：

- `GET /api/v1/cc/{tenant}/state` —— 只读、专用令牌（`brp.cc.token`，与
  agent 令牌分开）：配额余额、进行中的 DR 窗口、未来一小时 mspt 预测、
  信贷状态。一次轮询，没有历史。
- `cc-brp.lua` 作为后端静态资源随包发行：把端点包成 `brp.state()`、
  `brp.canRun()`——乌龟闸门只需用户再写十二行。
- 誊写练习（AE2/Create 深度适配器）留在 P5，compileOnly 轮盘赌也留在
  P5；P4 只发运不会腐烂的那条集成。

## 11. 表结构与配置汇总

迁移：**V19** 负载/电价/DR/信贷（`load_samples`、`load_profile`、
`tariff_windows`、`deferred_jobs`、`credit_lines`、`credit_events`）·
**V20** 债券/市场（`bond_series`、`bond_subscriptions`、`bond_accruals`、
`market_orders`、`market_trades`）· **V21** 车间（`downtime_events`、
`workorder_costs` 列、`assets`）。

配置键（默认值）：`brp.load.profile-alpha 0.05` ·
`brp.load.live-alpha 0.3` · `brp.load.live-damp 0.5` ·
`brp.load.retention-days 90` · `brp.dr.mspt-budget 40` ·
`brp.dr.award-every 60s` · `brp.dr.max-sheds 2` ·
`brp.credit.annual-rate-percent 12` · `brp.credit.utilization-watch 0.8` ·
`brp.credit.grace-days 7` · `brp.credit.limit-factor 0.25` ·
`brp.credit.limit-floor-cents 50000` · `brp.bond.threshold-mspt 45` ·
`brp.bond.factor-floor 0.5` · `brp.market.fee-bips 50` ·
`brp.market.order-ttl-days 7` · `brp.cc.token（空=关闭）`。

## 12. 任务板

| # | 任务 | 切片 | 状态 |
|---|---|---|---|
| P4-01 | V19 迁移：load_samples、load_profile、tariff_windows、deferred_jobs、credit_lines/events | A | ✓ |
| P4-02 | census 扩容：心跳带 players/entities/chunks/forced；分钟桶 upsert | A | ✓ |
| P4-03 | LoadForecastService：日周期 EWMA + 实时修正 + 小时预测 + 端点 | A | ✓ |
| P4-04 | TariffService + chunk 计费迁到表上（PeakPricingTest 保持绿） | B | ✓ |
| P4-05 | DemandResponseService：申报/授予/切除/过期 + 计费 | B | ✓ |
| P4-06 | mod：`dr.window` 信号 → governor feed 压线 | B | ✓ |
| P4-07 | CreditService：提款/还款/计息/状态机 + 熔断动作 | C | ✓ |
| P4-08 | BondService：发行/认购/计提/到期（performanceFactor） | D | ✓ |
| P4-09 | QuotaMarketService：订单、撮合、结算、费用、自成交禁令 | E | ✓ |
| P4-10 | 停机台账 + OEE（A×P，Q 披露）+ APS 页列 | F | ✓ |
| P4-11 | 工单完工成本 + 资产登记 | F | ✓ |
| P4-12 | CC:T 状态端点 + cc-brp.lua 资源 | G | ✓ |
| P4-13 | 门户负载页：预测 SVG、电价表、DR 窗口、信贷、债券、订单簿 | H | ☐ |
| P4-14 | OpenAPI 同步：load/credit/bonds/market/oee/cc 路径 | H | ☐ |
| P4-15 | 计数同步（AGENTS.md + 各 README）、docs/12 交接注记 | H | ☐ |
| P4-16 | 完成审计：后端全量 + mod 构建 + CI 双远端绿 | H | ☐ |

## 13. 测试计划

纯数学核心保持 static + 表驱动（P3 让 Gresham 改基线变成单文件评审的那套
打法）：`LoadForecastMath`（EWMA + 阻尼修正、冷启动）、`TariffMath`
（窗口边界、版本优先）、`DemandResponseMath`（窗口排序、装箱、切除顺延）、
`CreditMath`（计息、利用率阈值）、`BondMath`（计提、因子钳制、认购不足
退款）、`MarketMath`（价格-时间优先、部分成交、锚带校验）。集成：
`LoadSamplesTest`（心跳批次的分钟 upsert）、`DemandResponseTest`
（授予→信号→切除→退款走完 saga）、`CreditCutoffTest`（RESTRICTED 关区块、
禁 DR/市场、玩家不受影响）、`BondLifecycleTest`（托管→计提→到期→分币
结算）、`MarketMatchTest`（扫单、手续费、幂等重放、自成交跳过）、`OeeTest`
（从停机行算 A×P）。mod 单测：census 计数、DR 台路由、Lua 邻接 DTO 形状。

P4 非目标：期权及一切衍生的衍生（封存，docs/00）、保证金放贷、财政部
自己做市（手续费是财政部在市场里的唯一收入——自己跟自己对手盘的央行是
多绕一步的利益冲突）、AE2/Create 深度适配器（P5）。
