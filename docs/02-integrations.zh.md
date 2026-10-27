# 02 — 科技 Mod 兼容调研与适配设计 ⭐

> 英文版为主文档：[02-integrations.md](02-integrations.md)

> 调研时间 2026-10。标注 ✅=已验证 / ◆=高置信（训练知识，落地前对 javadoc 复核）/ ❓=需验证。
> 结论先行：**目标 1.21.1 NeoForge**；Create 锁定加载器；AE2 与 CC:T 是两个最高价值集成点。

## 1. 目标矩阵

| Mod | 版本 | 集成目标 | 优先级 |
|---|---|---|---|
| Applied Energistics 2 | 1.21.1 NeoForge ✅ | WMS：读 ME 网络库存；MRP：合成模拟做 BOM 可行性 | P0 |
| Create 6 | 1.21.1 NeoForge ✅ | 机器探针（转速/SU）；物流网络互操作；机械手/包装机自动化执行 | P0 |
| CC:Tweaked | 1.21.1 双平台 ✅ | BRP peripheral：Lua 直查库存/价格/下单/信号 | P0（性价比之王） |
| 无线红石（自建 + Create Redstone Link 桥接） | — | 信号中枢：频道制无线模拟信号 0-15 | P1 |
| Vanilla 容器/比较器 | — | 保底适配：箱子/木桶/漏斗 + 模拟量 | P0 |
| Carpet | 1.21.1 ✅ | 假人农场计量兼容、tick 性能数据源 | P2 |

## 2. AE2 集成设计

### 现状
- AE2 官方支持 NeoForge 1.21.1 ✅（appliedenergistics.org/download）
- 对外 API：`appeng.api` 包（✅ 存在且稳定多年），addon 通过它接入而不碰内部实现

### 切入点（◆ 复核点：appeng.api javadoc）
- **读网络库存**：放置"ME 桥"方块，注册为 AE2 grid 参与者（`IGridNode` 加入网络，订阅 `IStorageGrid`），
  监听 `IMEMonitor` 变更流 → 增量库存事件。同时读 `ICraftingGrid` 的 CPU 状态（在制工单！）。
- **合成模拟**：`ICraftingGrid` 的 crafting plan/simulation 接口 → **MRP 的 BOM 可行性 oracle**：
  后端说"要 64 TNT"，桥问 AE2 "原料够吗、几台 CPU、多久"，答案回传成产能数据。这比我们自己遍历 RecipeManager 做 BOM 展开更准（AE2 已处理替代品/优先级），两者并用：AE2 网络内用模拟，网络外用自有 BOM 引擎。
- **写入（发放/回收）**：ME 桥持有受限 `IActionSource`，只允许从指定 `IStorageCell` 子空间进出（配额映射到 cell 级别——cell 即租户仓库，多租户物理隔离，妙）。

### 风险
- AE2 API 跨版本重命名常见 → 适配层独立子模块 `brp-adapter-ae2`，compileOnly 依赖，反射注册兜底
- 2026-04 有 NeoForge 1.21.1 建新世界崩溃 issue（#8818）→ 集成测试矩阵要含 AE2 最新版

## 3. Create 6 集成设计

### 现状（✅ 已验证）
- Create 6.0 引入**物流网络**：Stock Link（把仓库挂上网）/ Stock Ticker（请求物品）/ Packager（打包装货，红石触发）
- Redstone Link 降为安山岩档，配新"发射器"材料——**Create 自带无线红石**，我们桥接而非重造
- 官方 wiki 有 "Depend on Create (NeoForge 1.21.1)" addon 开发指南
- 已有第三方 CC:T↔Stock Ticker API（CC Total Logistics）→ 证明物流网络可编程化是现实可行的

### 切入点
- **机器探针（Machine Probe）**：贴在动力方块上读 `KineticBlockEntity` 的转速/压力（◆）→ 上报 Backend 机器注册表。
  **SU（应力单元）利用率 = 天然容量规划指标**，仪表盘直接画"全服 SU 负载热力图"。
- **物流互操作（P1）**：BRP 工单下发 → 通过 Stock Ticker 语义请求物品 / 红石触发 Packager 发货。
  BRP 不复制 Create 的物流，而是**成为它的计划层**：Create 管运，BRP 管计划（这正是 MRP vs 物流的分工）。
- **机械手（Mechanical Arm）自动化**：BRP"工作站"方块暴露为 Arm 可交互目标（◆ `ArmInteractionPoint` 注册自定义交互点），
  机械手把原料放进 BRP 工作站 → 触发工单工序完成判定 → 产物被机械手取走。生电流水线接 ERP，吃满。
- **Display Link**：◆ Create 的 `DisplaySource` API 允许 addon 注册新数据源 → 做一个 "BRP 数据源"，
  游戏内告示牌直接显示后端 KPI（库存/价格/CPI）。讽刺感与实用度双拉满。

## 4. CC:Tweaked 桥（生电玩家杀手锏）

CC:T 是生电服标配（✅ 1.21.1 NeoForge/Fabric 双发）。给 BRP 做 peripheral（◆ `dan200.computercraft.api` 的
`IPeripheral` + `ComputerCraftAPI.registerPeripheralProvider`）：

```lua
-- 游戏内电脑里直接：
brp.stock("minecraft:iron_ingot")     -- 查全网络库存
brp.price("minecraft:tnt")            -- 查市场价/CPI
brp.order({tnt=64}, {approve=true})   -- 下单/审批
brp.signal("farm_north", 15)          -- 置位无线红石频道
brp.metrics("farm_north")             -- 农场产能
```

生电玩家用 Lua 把 BRP 编进自己的自动化 = 生态位直接从"管理工具"升级为"可编程平台"。
modem 无线 + 我们的信号中枢 = 跨区块控制系统。

## 5. 信号中枢（无线红石）设计

自建而非依赖单一 mod（Ranged Wireless Redstone 等维护状态不稳，❓ 26.x 前景未知）：

- **频道**：`@团队/频道名` 命名空间隔离；值域 0-15（对齐比较器模拟量语义）
- **实体**：信号中枢方块（接收端输出实体红石信号/比较器读数；发射端订阅频道置位）
- **控制路径**：Backend 推送 → Agent → 方块置位（策略引擎：库存超阈值自动关农场 = 配额联动）
- **兼容**：
  - Create Redstone Link：物理桥接（我们输出真实红石，Create link 采集/发射侧对齐即可），不碰其内部 API
  - 模拟量：比较器协议（0-15）天然兼容一切读取比较器的 mod（漏斗/熔炉计数的老传统）
- **安全**：频道所有权=租户；跨租户订阅需授权（防止"蹭别人家信号"）

## 6. BRP 方块清单（Roadmap 对齐）

| 方块 | 功能 | 对应模块 | 阶段 |
|---|---|---|---|
| 资源终端 Resource Terminal | 玩家游戏内 UI：查库存/下单/审批 | WMS/MRP | P1 |
| ME 桥 ME Bridge | AE2 网络接入（读库存/合成模拟/受限出入库） | WMS | P2 |
| 机器探针 Machine Probe | 动力方块/AE2 设备遥测（转速/SU/能耗） | SCADA | P2 |
| 流量计 Flow Meter | 物流路径计数（农场产能计量点） | 生电 | P2 |
| 信号中枢 Signal Hub | 频道制无线模拟信号 | 控制 | P2 |
| 工作站 Workstation | 工单工序节点（机械手可交互） | MRP | P3 |

## 7. 适配器 SPI（代码位置：`brp-mod/src/main/java/dev/brp/mod/spi/`）

```java
public interface BrpAdapter {            // 适配器注册入口
  String modId(); AdaptationPriority priority();
}
public interface InventoryProvider {     // AE2/原版/Create 物品柜统一抽象
  ResourceKey containerId();
  StockReport snapshot(DeltaSince since);  // 增量库存
  TransferResult transfer(TransferCmd c);  // 受限出入库（幂等键必带）
}
public interface MachineProbe {          // 机器遥测
  MachineStats sample();                 // rpm / SU / 能耗 / 卡死标志
}
public interface SignalPort {            // 信号置位执行
  void set(String channel, int level0to15, Cause cause);
}
```

原则：**SPI 不 import 任何 MC/mod 类**（纯 Java + 自定义类型），MC/mod 交互全部压在各 adapter 子模块里。
好处：单测不用起 MC、26.x 移植时 SPI 与业务逻辑零改动。

## 8. 村民改良类 mod（Easy Villagers 一族）

henkelmax 的 Easy Villagers（CurseForge 6900 万下载，NeoForge 1.21 在编 ✅）是
目录案例：村民物品化，外加 Trader / Auto Trader / Breeder / Converter /
Incinerator 方块。对 docs/03 §10 的威胁模型：**方块内执行的交易完全绕过原版
商人菜单**——`MerchantResultSlot` 挂点永远不会触发，持牌铸币厂的水表恰好在
自动化最密集处失明。适配器规则（ModList 守卫、软依赖、§7 同款纪律）：

- **Auto Trader 产出**用容器增量计量（对方块槽位做 `inventory.snapshot`），
  绝不挂 GUI 钩子；适配器从进出物品流反推等价交易数
- **Converter 方块**是工业级 `villager.cured` 发射器——05 稿的折扣取证必须
  归因它们，否则普查会在治愈途中"丢人"
- **Breeder 方块**是受许可机器（`machine_slot`）：经设备的人头增长与自然
  繁殖同样路由进 `villager` 配额，无豁免
- **村民物品化**会把实体移出世界——携带期间区块归属与普查双双失明 ◆；
  适配器把物品态村民计入携带租户的人头
- **Incinerator**：第三方处决块存在；BRP 计量人口差并逐笔记为**未批准的
  资产减记**（docs/07 §7）。BRP 自己的表单永不路由到那里——村民是稀缺
  人头，不是库存。

SPI 增补（仅加法）：`TradeSource extends BrpAdapter`，按容器上报物品等价的
交易增量——§7 的纯 Java 规则原样适用。

## 来源

- Create 6.0.0 Changelog（物流网络引入）: https://wiki.createmod.net/users/changelogs/6.0.0
- Depend on Create / NeoForge 1.21.1: https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1
- Stock Link / Stock Ticker: https://create.fandom.com/wiki/Stock_Link · https://create.fandom.com/wiki/Stock_Ticker
- CC Total Logistics（第三方 CC:T↔Create 物流 API 的存在性证据）: https://xeli-cloud.gitlab.io/website/2025/05/11/cc-logistics-api-0.2.0.html
- AE2 下载页（NeoForge 1.21.1 官方支持）: https://appliedenergistics.org/download
- Easy Villagers（henkelmax；Trader/Auto Trader/Breeder/Converter/Incinerator）: https://www.curseforge.com/minecraft/mc-mods/easy-villagers
