# 06 — 幂等、Outbox、Saga、账本并发 ⭐

> 英文版为主文档：[06-transactions.md](06-transactions.md)

> 分布式系统没有 exactly-once，只有"至少一次投递 + 恰好一次效果"。
> 本文档是后端与 Agent 共同遵守的事务契约。

## 1. 事件信封（契约见 brp-contract/events.md）

```json
{
  "eventId":    "uuid",        // 全局唯一，重放安全的第一道防线
  "idempotencyKey": "uuid",    // 业务幂等键（发起方生成）
  "schemaVersion": 1,
  "agentId": "ag-x", "tenantId": "t-1",
  "type": "inventory.delta", "occurredAt": "...", "payload": {}
}
```

## 2. 幂等（Idempotency-Key 契约）

- 所有写操作：客户端生成 UUID 作 `Idempotency-Key` 头（或信封字段）
- 服务端：`idempotency_keys(key PK, response_hash, created_at)`；事务内先插后做——
  **唯一约束冲突 = 重复请求 → 返回首次结果**（响应快照回放，客户端拿到语义相同的答复）
- 响应快照过期 24h；过期后重复请求返回 410 + 指引重新发起
- 网络超时：客户端**必须**用同 key 重试，退避 + 抖动（1s/2s/4s…±30%）

## 3. Outbox（两端各自持久化后才算发出）

**Agent 侧（文件 WAL）**：
```
事件 → append 到 queue 目录顺序文件（fsync 可调）→ 后台发送线程批量 POST
→ 收到 ack(所有 eventId) → 推进 watermark，截断已确认段
```
Backend 宕机时文件无限增长？→ 磁盘水位保护：超阈值丢最老的**遥测类**事件（遥测可丢，
账务/指令类永不丢，分类见 events.md 的 `reliability` 字段）。

**Backend 侧（DB outbox）**：
业务事务内写 `outbox(event_id, payload, status)`（同库同事务 = 原子）→
独立 dispatcher 轮询投递（推 SSE / Discord / webhook）→ 至少一次 + 下游按 event_id 幂等。

## 4. Saga（跨模块长事务，补偿而非回滚）

例：团队金库领料审批通过后执行：
```
1. 账本: 冻结额度(escrow)   ──失败──► 结束（什么都没发生）
2. 库存: ME 桥出库(幂等键k) ──失败──► 补偿: 解冻额度
3. 账本: 冻结→实扣          ──失败──► 补偿: 回滚库存(幂等键k')，解冻
4. 通知: Discord/信箱       ──失败──► 仅重试，不回滚（非关键步骤）
```
实现：saga 状态机表（`saga_instances`），每步幂等、可重入；补偿步骤也带幂等键。
规则：**账本冻结是万能补偿点**——先冻结再做事，任何一步失败都能安全收场。

## 5. 账本并发与一致性

- `journal_entries` append-only，禁止 UPDATE/DELETE（DB 层 revoke 权限双保险）
- 分录原子性：一个事务内 insert 全部分录行 + idempotency_keys 行，靠约束保证：
  - `CHECK` 借贷平衡（分录组级别用事务内断言，行级存 amount_signed）
  - `UNIQUE(idempotency_key)`
- 余额并发：热点账户（系统商店）用**分片余额**（N 个子账户轮转）缓解行锁竞争；普通账户乐观锁足够
- 对账 job（每日，05 §3 的三条不变量）+ Agent↔Backend 流量计计数校验（抽样对时间窗求和）

## 6. 事件版本化

- `schemaVersion` 只加不减；后端兼容 N 与 N-1 两个大版本
- 破坏性变更 = 新 type 名 + 旧 type 冻结通知期（两个版本周期），Agent 拒绝升级则停用新功能不下线旧链路

## 7. 注解套件（brp-common，后端与 agent 共享）

| 注解 | Backend 语义 | Agent 语义 |
|---|---|---|
| `@Idempotent(key, ttl)` | SpEL 键；`idem_store` 存响应；重放返回首响应；并发由 PK 裁决 | 入队前按 eventId/键做 WAL 去重 |
| `@Cached(key, ttl, maxSize)` | Caffeine，写入起算 TTL，null 不缓存 | CacheFront（Caffeine 包装，可整体重定位） |
| `@Atomic(timeoutMs)` | 一个数据库事务，全有或全无 | LocalStore 的一个 SQLite 事务 |
| `@Compensable(saga, step, compensation)` | 持久化 RUNNING→DONE/FAILED 行；SagaEngine 自动反向补偿 + 崩溃恢复 | 记入本地日志，随遥测上传 |

切面顺序（外→内）：`@Cached` → `@Idempotent` → `@Atomic` → `@Compensable`
——缓存命中短路一切，重放跳过事务，saga 步骤保持原子。

诚实条款：`@Idempotent` 在成功**之后**存响应——效果与存储之间崩溃可能重放执行。因此资金类操作用账本的同事务幂等行（§2/§5），不用这个切面。

## 9. 加固后的不变量（2026-10 代码评审轮）

- 幂等键按租户隔离（V9 复合主键 (tenant_id, idem_key)）：重放永不跨租户；
  同一个键在另一个租户是另一笔操作。
- 完整性冲突只有在"本租户键行确实存在"时才走重放；其余带着真实原因上抛。
- Saga 行以 REQUIRES_NEW 写入（SagaRecorder）：审计记录在调用方回滚后依然存在，
  SagaRecovery 恢复的是事实而非虚构。
- 恢复按 (saga, correlation) 定界——业务 correlation 会撞车——并按定义逆序补偿。
- 分录校验币种一致、服务端正数兜底，TOCTOU 账户消失按 400 处理而非 500。
