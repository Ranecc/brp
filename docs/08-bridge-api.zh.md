# 08 — 桥接模组 API（外部集成）

> 英文版为主文档：[08-bridge-api.md](08-bridge-api.md)
>
> BRP 为第三方桥接 mod 暴露**稳定**接口：其他玩家可把他们的 mod
> （科技/魔法/物流——什么都行）接入账本、配额与信号，无需 shade 我们
> 的内部实现。不在这份文档里的，就不是 API。

## 1. Mod 侧接口面（`dev.brp.api`，semver，只增不减）

```java
// 经 Java ServiceLoader 发现（不耦合 NeoForge）
public interface BrpBridge {
    void publish(EventDto event);                    // -> agent WAL，至少一次
    CompletableFuture<Double> stock(String itemId);  // 团队范围读取
    CompletableFuture<Boolean> signal(String channel, int level0to15);
    void onLedgerEvent(Consumer<LedgerEvent> consumer); // 已入账事件扇出
}
```

- 桥接 mod 只依赖 `brp-common`（注解语义：`@Idempotent` 在 WAL 边界兑现）
  ——零 shade 依赖。
- `EventDto` 原样复用遥测信封；事件类型前缀 `ext.<modid>.*`，注册表保持整洁。
- publish 永不阻塞调用方：交给 WAL 即返回（红线 3 对客人同样生效）。
- 版本化：`dev.brp.api` 走 semver；破坏性变更开 `v2` 包 + 两版本冻结
  （与事件 schema 同一纪律）。

## 2. Backend 侧接口面

第三方集成用**同一份** REST 契约（brp-contract），配范围化机器令牌
（像 Agent 一样注册，按租户只读或命令范围；P2）。永远不开私有端点——
OpenAPI 文件就是全部真相。

## 3. 治理

- 桥接注册契约先行：`ext.*` 事件类型先进 events.md 再写代码。
- 滥用姿态：桥接事件默认 TELEMETRY 级；碰钱的必须走显式签发的
  CRITICAL 范围 + 独立审计线。
- 本 API 的第一方消费者：Create/AE2/CC:T 适配器自己（docs/02）——
  我们吃自己的狗粮桥。
