# P5 — 交易所：批量拍卖、挂牌铸币厂与复本位闭环

> 状态：**已交付** · 阶段：P5
> 前置：docs/13（P4 负载台）· 血缘前置：docs/03 §3、§9、§10
>
> P4 解决的是*算力何时被使用*。P5 建的是给其余一切定价的那间屋子：
> 一座周期批量清算的物品交易所（R-8）、一家保证市场永不落空的
> **挂牌铸币厂**，以及为 docs/03 §9 的复本位标准收尾的机制——peg
> 带终于会*自己观测*市场，而不是等人喂数。三百年货币学文献，
> 一条 H2 兼容的迁移脚本。

## 1. 实地调研：三份文献，按供应商手册的方式引用

拒绝口口相传。每一条都对应 §3–§6 中的某个机制。

| 论文 | 主张 | BRP 借走什么 |
|---|---|---|
| Velde & Weber,《复本位模型》（JPE 108:6, 2000）；Oppers，《复本位制模型》（IMF WP 95/144） | 固定**铸币比率**能让两种货币并行流通；市场比率在*兑换点之间*徘徊，货币当局真的能稳定这个体系 | 我们的 emerald/quota 双腿（docs/03 §9）就是铸币比率；±5% 带就是兑换点对。P5 补上缺的另一半：兑换带**自动观测**市场清算（§7） |
| Sargent & Velde,《小额零钱的大问题》（Princeton, 2002） | 数百年零钱短缺，根源是铸币厂不随时按固定比率凭准备金双向兑换。解法——**标准公式**：钉住代币、持有兑换准备金、*无限量*双向兑换 | 挂牌铸币厂（§4）：挂牌价、仓库准备金、*诚实受准备金约束*的无限供给。「没有玩家卖→没东西买」就是零钱病；标准公式是药方 |
| Budish, Cramton & Shim，《高频交易军备竞赛》（QJE 130:4, 2015） | 用**频繁批量拍卖**取代连续限价订单簿：每批单一清算价、消灭延迟军备竞赛、薄市场照常运转 | R-8，docs/10 已采纳，§6 落地。「9:00 开市，9:05 收市，一如所有伟大的流动性匮乏市场」——现在每 5 分钟一次，带仪表盘 |
| 集合竞价实务（Xetra/Euronext 开盘拍卖）；Vickrey 1961 | 清算价规则：最大化可成交量 → 最小化失衡 → 锚定参考价。单一价格使真实报价成为占优策略 | `AuctionMath`（§6）：拐点算法 + **以铸币参考价为锚**——薄订单簿不该在 101 的卖单和 1000 的买单中间打印个中间价 |
| docs/03 §3（价格源）、§10（村民大厅）、docs/10 R-6 | `market.price.observed` 进料口已存在；大厅是持证铸币厂；CPI 把清算价排在商店报价之上 | 交易所清算喂 `MarketPriceService.record`（`venue="bourse"`）——CPI 不用再等那份永远不来的遥测 |

## 2. 供给模型：S = S_铸币 + S_玩家

腐竹的定价与玩家的自由在一本订单簿里相遇，而不是开两家店：

- **S_mint（基础供给）**——挂牌铸币厂每批对每件物品挂两张报价：买价
  `ref × (1 − halfSpread)`、卖价 `ref × (1 + halfSpread)`，各挂 `depth`
  件。它是*一张挂着常备订单的参与席位*，不是平行商店——清算算法
  从不关心、也无从知道哪部分量是「国家的」。
- **S_players**——租户限价单，跨批持久，直到成交、撤销或过期。

模型按构造保证的性质：

- **市场永不落空。** 没人卖时，铸币厂卖单是账上唯一一行，照样清算。
  玩家压价时，流量归玩家——价格演化由市场驱动，铸币厂扮演剩余吸收者，
  正是 Velde & Weber 派给当局的补偿性角色。
- **准备金是真的。** 铸币厂卖不出仓库里没有的货，也买不起付不起的单
  （`treasury + brp.exchange.mint-credit-cents`，授信默认 0）。买得起不付
  钱的铸币厂是刷了漆的印钞机。负值浮动余额是一行*显性负债*——
  P4 债券金库（bond-sink）的先例。
- **铸币税是铸造利差，逐笔计量。** 铸币厂吃到的价差停在它的资金池里；
  每日清扫把超额划入 SYSTEM_SINK（幂等键 `mint-seigniorage:{date}`）。
  工作余额由 `brp.exchange.mint-float-cents` 保留（默认 0——全扫）。

## 3. 标的与身份

- 物品身份是**命名空间注册表 id**（`minecraft:emerald`、
  `ae2:certus_quartz_crystal`，模组目录推什么有什么）。模组物品从第一
  天起就是一等公民；显示名推送时自动美化，腐竹可改。
- 货币是 BRC 分，同一账本、同一批账户（`market-escrow`、
  `market-clearing`、`market-sales`、`market-fee`、`treasury`），与配额
  市场共用。流水备注 `bourse:{itemId}`——刻意**不打 `rail:` 标签**：
  两条 rail 是铸币窗口和配额市场（docs/03 §9）；交易所是价格发现场所，
  不是 rail，Gresham 台的基线不许被污染。
- 铸币厂主体是伪租户 `mint`（它的仓库行和 BRC 账户住在同一批表里；
  腐竹向该租户入仓即完成注资）。

## 4. 挂牌铸币厂

每件已挂牌物品由腐竹挂出：`ref_cents`（挂牌时必填——国家就是要给世界
定价）、`pinned`（可选），批级报价一律派生，绝不配两处配置：

```
bid = ref − ref × halfSpread      ask = ref + ref × halfSpread
halfSpread = brp.exchange.mint-spread-bips / 20000   （默认 1%）
depth      = brp.exchange.mint-depth           （默认 64 件）
```

**价格演化（爬行）。** 每批清算后，若 `cleared_qty ≥
brp.exchange.ref-min-qty`（默认 8）且 `pinned = false`：

```
ref ← ref + brp.exchange.ref-crawl × (clear − ref)    （默认 0.25）
```

一手成交不许拖着挂牌价跑；八手才算市场。钉住的物品永不爬行——
腐竹的名录是主权的，「挂牌」说的就是这个；货币事项的唯一治理通道
仍然是 re-peg 工作流（docs/03 §9）。emerald 那行演示了这出喜剧：
它的参考价随市场爬行，peg 带盯着同一个市场，分歧超过 ±5% 时由
OWNER 投票裁决。铸币比率和挂牌价一天吵两次，这就是产品。

**铸币厂的开市挂牌。** V22 只种一行：`minecraft:emerald`，LISTED，
`ref = 6400`（emerald 腿比率，docs/03 §9），不钉。其余的等腐竹挂、
等目录推。

## 5. 挂牌治理：两种预设（顺便回答村民问题）

单物品状态：`DEFAULT | LISTED | BLOCKED`。裁决规则：`BLOCKED` 永不
交易；`LISTED` 永远可交易；`DEFAULT` 跟随预设：

| 预设 | `brp.exchange.preset` | DEFAULT 的含义 | 腐竹的杠杆 |
|---|---|---|---|
| **自由市集 FREE_BOURSE**（默认） | `free` | 可交易 | 一份问题物品封禁单 |
| **名录商行 CURATED_CATALOG** | `curated` | 不挂名录不动 | 一份白名单；没有行就没有交易 |

**村民问题。** 如果交易所按铸币价兜底供应村民卖的货，村民就只剩一项
竞争优势——带腿的农场。他们的独占报价（附魔书、大师级盔甲）会和仓库
库存变得无法区分，docs/03 §10 的持证大厅会退化成原料收购站。平台不站队、
不修管道：**绝不从 `merchant.trade` 遥测自动挂单**，村民的货不会自己
走进订单簿。两种预设就是腐竹的杠杆：选自由市集的腐竹拿到有效率的
市场，顺便给村民博物馆捐了展品；选名录商行的腐竹只要永远不挂村民
独占品，就保住了村民溢价。治理行由人来定价，因为这本该是人做的决定。

## 6. 交易所：批量清算（R-8）

**V22 模式：**

```
exchange_items(item_id PK, display_name, listed_state DEFAULT|LISTED|BLOCKED,
               ref_cents, pinned, created_at, updated_at)
warehouse(tenant_id, item_id, qty_free, qty_held, PK(tenant_id, item_id))
exchange_movements(id PK, tenant_id, item_id, delta_free, delta_held,
                   kind DEPOSIT|WITHDRAW_OUT|WITHDRAW_RETURN|HOLD|RELEASE|
                        FILL_OUT|FILL_IN,
                   ref_id, idem_key UNIQUE, created_at)
exchange_orders(id PK, tenant_id, item_id, side ASK|BID, qty, qty_remaining,
                price_cents, status NEW|PARTIAL|FILLED|CANCELLED|EXPIRED,
                expires_at, idem_key UNIQUE, created_at)
exchange_claims(id PK, tenant_id, item_id, qty,
                status PENDING|CONFIRMED|RELEASED, created_at)
exchange_batches(batch_seq PK, cleared_at, items_cleared, trades_cleared)
exchange_trades(id PK, batch_seq, item_id, buyer, seller, qty, price_cents,
                fee_cents, bid_order_id, ask_order_id, created_at)
exchange_clears(batch_seq, item_id, price_cents, cleared_qty, bid_vol,
                ask_vol, surplus, mint_bid_qty, mint_ask_qty, ref_before,
                ref_after, PK(batch_seq, item_id))
```

**订单模型。** 只收限价单。BID 在申报时托管 `qty × price` 的 BRC（P4
套路）；ASK 把 `qty` 从 `qty_free` 移进 `qty_held`。撤销/过期即释放。
未成交余量留到下一批。`brp.exchange.order-ttl-days`（默认 7）过期清理
陈单——面对常备双边做市本来也不会有单躺到永远，但无聊就要无聊到底。
清算配对时同一租户两边都占：跳过（配额市场先例）。

**清算算法（`AuctionMath`，纯函数，测到怀疑人生）：**

1. 候选价：所有去重订单价加上铸币厂两张报价。
   `vol(P) = min(Σ 买量(price ≥ P), Σ 卖量(price ≤ P))`，含铸币腿；
   铸币深度已按准备金封顶（§2）。
2. P* 最大化 `vol`；并列则最小化 `|买量 − 卖量|`；再并列则**锚定该物品
   的 ref**（取最近者；最终并列取低价）。锚定规则正是薄订单簿打出
   101 而不是 101 与 1000 的中间价的原因——集合竞价为此锚定参考价。
3. P* 处的配额分配：严格优于 P* 的单按时序全成；等于 P* 的单按时序
   吃满剩余量；分配阶段跳过同租户对（实际成交量可能低于 `vol(P*)`——
   钱只在已实现成交上移动，守恒无恙；已记录的简化）。
4. **单一价格**：每笔成交、含铸币腿，一律按 P* 打印。这是 Budish 论文
   的全部意义，也是 Vickrey 1961 的全部意义。

**结算流水**（每笔成交；配额市场的形状，备注 `bourse:{item}`）：
买方 `market-clearing DEBIT / market-escrow CREDIT`（全额）；卖方
`treasury DEBIT (全额 − 手续费)`、`market-fee DEBIT fee`，对侧
`market-sales CREDIT 全额`。`brp.exchange.fee-bips`（默认 50）就是
docs/03 §4 的拍卖佣金旋钮。铸币厂在 `mint` 租户下按同样规则结算；
价差沉淀进它的资金池，每日清扫收走（§2）。

**批执行**（`ExchangeClearingService.runBatch()`）：每个 `batch_seq`
先写一条 insert-only 守卫行（`Persistable` isNew——ProfileRun 套路），
然后整批一个事务：按物品拉常备单、派生铸币报价、清算、结算、爬价、
记录清算、喂 CPI（`MarketPriceService.record(..., "bourse", "clear")`）、
别的不发。`@Scheduled(fixedDelayString + initialDelayString =
${brp.exchange.clear-ms:300000})`——P4 开机伏击的教训，代代相传。
另留手动触发端点给运维和测试。

## 7. 复本位闭环

`brp.peg.auto-observe`（默认**开**）：每批之后，`minecraft:emerald` 的
清算调用 `peg.observe("emerald", price/100)`；每笔配额市场成交调用
`peg.observe("quota", price/100)`。±5% 带不再等人喂观测——市场自己打
电话进来，既有机器（连续越界 2 次 → REPEG_VOTE → OWNER 阶梯）接管
其余。本节制作过程中没有创建任何第三条治理通道。

## 8. 模组端：目录与仓库

- **目录推送**（P5-09）：代理枚举注册表物品 id（`BuiltInRegistries.ITEM`
  keySet ◆ 对照 1.21.1 映射核实），每 500 个 id 一批
  `POST /api/v1/exchange/catalog`，启动推 + 变更推。后端只 upsert 显示名
  ——挂牌状态永远归治理管。
- **仓库**（P5-10）：绑定容器（`brp.vault.container`）是实物对端。
  **入仓**：代理扫描、把行暂存进 LocalStore、申报入仓、收到 ack 再取走
  实物——货物先离开世界、账本后移动；P3 铸币定序规则方向相反、理由
  相同。**出仓**：申报 claim → 后端扣 free 返回 claim → 代理发放实物
  → 确认；过期 claim 回冲（SagaRecovery 清扫）。**守恒**：仓库的每一次
  变动都写一条 append-only 移动行（`idem_key` 唯一）——对账科（R-1）白捡
  两条恒等式：按物品与租户，`warehouse ≡ Σ movements`，且
  Σ `qty_held` ≡ 在场 ASK 余量。

## 9. 任务板

| # | 切片 | 交付物 | 状态 |
|---|---|---|---|
| P5-01 | A | V22 迁移、实体、仓储接口 | ✓ |
| P5-02 | A | 目录 + 挂牌治理（两种预设）+ 推送 API | ✓ |
| P5-03 | B | 仓库（存/取/claim/移动账，守恒） | ✓ |
| P5-04 | C | `AuctionMath` 拐点算法（纯函数） | ✓ |
| P5-05 | D | 订单申报（托管/占用、幂等、撤销） | ✓ |
| P5-06 | E | 挂牌铸币厂（报价、铸币税清扫） | ✓ |
| P5-07 | E | 批量清算调度（单事务、守卫、initialDelay） | ✓ |
| P5-08 | E | 参考价爬行 + CPI 进料 + 复本位闭环（peg 自动观测） | ✓ |
| P5-09 | F | 模组：物品目录推送 | ✓ |
| P5-10 | F | 模组：金库暂存式仓库存取 | ✓ |
| P5-11 | G | 门户交易所页 + 控制器端点 | ✓ |
| P5-12 | G | OpenAPI 路径 + 消息文案（en/zh） | ✓ |
| P5-13 | G | 测试计数同步（AGENTS.md、README） | ✓ |
| P5-14 | H | 审计：共享 H2 隔离、调度竞态、幂等重放 | ✓ |

## 10. 配置

| 键 | 默认 | 含义 |
|---|---|---|
| `brp.exchange.preset` | `free` | `free`（自由市集）/ `curated`（名录商行） |
| `brp.exchange.clear-ms` | `300000` | 批节奏；同时是调度器 initialDelay |
| `brp.exchange.fee-bips` | `50` | 拍卖佣金（docs/03 §4 旋钮） |
| `brp.exchange.mint-spread-bips` | `200` | 围绕 ref 的全价差（每边一半） |
| `brp.exchange.mint-depth` | `64` | 铸币厂每边每批报价量 |
| `brp.exchange.mint-credit-cents` | `0` | 铸币厂可超出浮动资金竞买的额度 |
| `brp.exchange.mint-float-cents` | `0` | 清扫前保留的铸币厂工作余额 |
| `brp.exchange.ref-crawl` | `0.25` | 向清算价的 EWMA 系数 |
| `brp.exchange.ref-min-qty` | `8` | 爬行所需最小成交量 |
| `brp.exchange.order-ttl-days` | `7` | 常备单过期 |
| `brp.peg.auto-observe` | `true` | 清算喂 peg 带 |

## 11. 测试计划

- `ListingPolicyTest`——两种预设 × 三状态 × 裁决矩阵；目录推送只美化
  名字，不碰治理。
- `WarehouseTest`——存/取/claim；混合移动序列后的守恒式；幂等重放；
  claim 释放回冲。
- `AuctionMathTest`——拐点表：最大量、失衡并列、ref 锚（101/1000 案例）、
  铸币腿参与、同租户跳过、空簿。
- `BourseOrderTest`——BID 托管 / ASK 占用、撤销释放、信贷 RESTRICTED
  拒单、幂等重放。
- `ExchangeMintTest`——报价受准备金与深度约束；清扫保留 float、按日期
  幂等。
- `BourseClearingTest`——整批：守卫 insert-only、单事务结算、未成交
  留存、爬行只在 min-qty 之上动、钉住永不动、CPI 行落地、emerald 清算
  喂进 peg.observe。
- 共享 H2 纪律照抄 P4：断言圈定自己的行/租户；peg 断言读实时带；
  注入 Clock bean；`@AfterEach` 清理全局表。
- 模组单测：目录分批、暂存/claim 状态机。

## 12. 非目标

- **TPS 期权永久封存**（docs/00）。期货已由 P4 债券交付；讽刺配额到此
  为止。
- 不做空（卖你持有的），不做保证金，不出借。
- 没有连续交易阶段——交易所要么批量清算，要么不清算（R-8）。
- 不给订单簿修村民遥测管道（§5）。大厅保留执照；交易所袖手旁观。
- 永远没有真钱（红线 7）。交易所用物品交易游戏币，审计科逐笔读这份
  笑话的两边。
