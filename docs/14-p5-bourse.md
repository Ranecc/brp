# P5 — The Bourse: batch auctions, a listed mint, and the bimetallic closure

> Status: **shipped** · Phase: P5
> Predecessor: docs/13 (P4 load desk) · Predecessor-in-law: docs/03 §3, §9, §10
>
> P4 priced *when the ticks get used*. P5 builds the room where everything
> else gets priced: an item exchange with periodic batch clearing (R-8), a
> **listed mint** that guarantees the market is never empty, and the
> mechanism that closes docs/03 §9's bimetallic standard — the peg band
> finally *observes* the market instead of waiting to be told. Three
> centuries of monetary literature, one H2-compatible migration.

## 1. Field research: three literatures, cited like vendors

No folklore. Each entry maps to a mechanism in §3–§6.

| Paper | Claim | What BRP borrows |
|---|---|---|
| Velde & Weber, *A Model of Bimetallism* (JPE 108:6, 2000); Oppers, *A Model of the Bimetallic System* (IMF WP 95/144) | A fixed **mint ratio** sustains concurrent circulation of two monies; the market ratio idles *between conversion points* and the authority can genuinely stabilize the system | Our emerald/quota peg legs (docs/03 §9) are the mint ratio; the ±5% band is the conversion-point pair. P5 adds the missing half: the band **observes** market clears automatically (§7) |
| Sargent & Velde, *The Big Problem of Small Change* (Princeton, 2002) | Centuries of coin shortages came from mints that did not stand ready to exchange at a fixed rate against a reserve. The fix — the **standard formula**: peg the token, hold a convertibility reserve, exchange *unlimited* quantities both ways | The listed mint (§4): posted (挂牌) prices, a warehouse reserve, unlimited supply *bounded honestly by the reserve*. "No players selling → nothing to buy" is the small-change disease; the standard formula is the cure |
| Budish, Cramton & Shim, *The High-Frequency Trading Arms Race* (QJE 130:4, 2015) | Replace continuous limit-order books with **frequent batch auctions**: uniform clearing price per batch, no latency arms race, thin markets included | R-8, accepted in docs/10 and built in §6. "Open at 9:00, closed at 9:05, like all great thin markets" — now every 5 minutes, with dashboards |
| Call-auction practice (Xetra/Euronext opening auctions); Vickrey 1961 | Clearing price rule: maximize executable volume → minimize imbalance → anchor to reference. Uniform price makes truthful bidding dominant | `AuctionMath` (§6): the kink algorithm with the **mint reference as anchor** — a thin book must not print midpoint prices between a 101 ask and a 1000 bid |
| docs/03 §3 (price sources), §10 (villager hall), docs/10 R-6 | `market.price.observed` feed exists; the hall is a licensed mint; CPI ranks clears above shop quotes | Bourse clears feed `MarketPriceService.record` with `venue="bourse"` — CPI stops waiting for telemetry that never came |

## 2. The supply model: S = S_mint + S_players

The owner's pricing and the players' freedom meet in one order book, not two
shops:

- **S_mint (base supply)** — the listed mint stands in every batch with two
  quotes per item: a bid at `ref × (1 − halfSpread)` and an ask at
  `ref × (1 + halfSpread)`, each for `depth` units. It is a *participant
  with standing orders*, not a parallel storefront — the clearing algorithm
  never knows or cares which volume was "the state's".
- **S_players** — tenant limit orders, persisted across batches until filled,
  cancelled, or expired.

Consequences the model guarantees by construction:

- **The market is never empty.** If nobody sells, the mint ask is the only
  row on the book and the batch still clears. If players undercut the mint,
  players get the flow — price evolution is market-driven and the mint is
  the residual absorber, exactly the compensatory role Velde & Weber assign
  the authority.
- **The reserve is real.** The mint cannot ask what its warehouse lacks, and
  cannot bid what it cannot pay (`treasury + brp.exchange.mint-credit-cents`,
  credit default 0). A mint that can buy without paying is a printer with a
  coat of paint. Outstanding negative float is a *visible liability* line —
  the bond-sink precedent (P4).
- **Seigniorage is brassage, metered.** The spread the mint captures sits in
  the mint treasury; a daily sweep moves the excess to SYSTEM_SINK
  (idem `mint-seigniorage:{date}`). The mint's float working balance is
  `brp.exchange.mint-float-cents` (default 0 — sweep everything).

## 3. Instruments and identity

- Item identity is the **namespaced registry id** (`minecraft:emerald`,
  `ae2:certus_quartz_crystal`, anything the mod catalog pushes). Mod items
  are first-class from day one; the display name is prettified on push and
  editable by the owner.
- Money is BRC cents, same ledger, same accounts (`market-escrow`,
  `market-clearing`, `market-sales`, `market-fee`, `treasury`) as the quota
  market. Journals carry memo `bourse:{itemId}` — deliberately **no
  `rail:` tag**: the rails are the mint window and the quota market (docs/03
  §9); the bourse is the price-discovery venue, not a rail, and the Gresham
  desk's baselines stay clean.
- The mint principal is the pseudo-tenant `mint` (its warehouse rows and
  BRC accounts live in the same tables; the owner provisions it by
  depositing under that tenant).

## 4. The listed mint (挂牌铸币厂)

Per listed item the owner posts: `ref_cents` (mandatory at listing — the
state does price the world), `pinned` (optional), and the batch quote is
derived, never configured twice:

```
bid = ref − ref × halfSpread      ask = ref + ref × halfSpread
halfSpread = brp.exchange.mint-spread-bips / 20000   (default 1%)
depth      = brp.exchange.mint-depth           (default 64 units)
```

**Price evolution (the crawl).** After each batch that clears an item with
`cleared_qty ≥ brp.exchange.ref-min-qty` (default 8) and `pinned = false`:

```
ref ← ref + brp.exchange.ref-crawl × (clear − ref)    (default 0.25)
```

One lot must not drag the posted price; eight is a market. Pinned items
never crawl — the owner's list is sovereign, that is what 挂牌 means, and
the re-peg workflow (docs/03 §9) remains the only governance channel for
money matters. The emerald row demonstrates the comedy: its ref crawls with
the market while the peg band watches the same market, and when they
disagree past ±5%, an OWNER vote settles it. The mint ratio and the posted
price argue twice a day; that is the product.

**The mint's founding listing.** V22 seeds exactly one row:
`minecraft:emerald`, LISTED, `ref = 6400` (the emerald leg rate, docs/03 §9),
unpinned. Everything else waits for the owner or the catalog push.

## 5. Listing governance: two presets (the villager question, answered)

Per-item state: `DEFAULT | LISTED | BLOCKED`. Resolution: `BLOCKED` never
trades; `LISTED` always trades; `DEFAULT` follows the preset:

| Preset | `brp.exchange.preset` | DEFAULT means | Owner's lever |
|---|---|---|---|
| **FREE_BOURSE** (default) | free | tradable | a block list for problem items |
| **CURATED_CATALOG** | curated | inert until listed | a whitelist; nothing moves without a row |

**The villager question.** If the exchange supplied villager-sold goods at
mint prices, villagers would keep exactly one competitive advantage — being
farms with legs. Their exclusive offers (enchanted books, masterwork armor)
would become indistinguishable from warehouse stock, and docs/03 §10's
licensed halls would degenerate into raw-input intake. The platform takes no
side and builds no pipe: there is **no auto-listing from `merchant.trade`
telemetry**, villagers never walk their wares onto the order book by
themselves. The presets are the owner's lever: a free-bourse owner gets the
efficient outcome and funds the villager museum; a curated owner preserves
the villager premium by never listing villager exclusives. Governance rows
are priced by a human, because that is the decision a human should own.

## 6. The exchange: batch clearing (R-8)

**V22 schema:**

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

**Order model.** Limit orders only. A BID escrows `qty × price` BRC at
filing (P4 pattern); an ASK moves `qty` from `qty_free` to `qty_held`.
Cancel/expire releases both. Unfilled remainders persist to the next batch.
`brp.exchange.order-ttl-days` (default 7) expires stale orders — against a
standing two-sided mint nothing rests forever anyway, but boring is boring.
Same tenant on both sides of an allocation: skipped (quota-market precedent).

**The clearing algorithm (`AuctionMath`, pure, unit-tested to death):**

1. Candidate prices: all distinct order prices plus the mint's two quotes.
   `vol(P) = min(Σ bidQty(price ≥ P), Σ askQty(price ≤ P))` including mint
   legs; mint depth already capped by reserve (§2).
2. P* maximizes `vol`; ties minimize `|bidVol − askVol|`; remaining ties
   **anchor to the item's ref** (closest wins; final tie: lower price). The
   anchor rule is why a thin book prints 101 instead of a midpoint between
   101 and 1000 — call auctions anchor to the reference for exactly this
   reason.
3. Allocation at P*: orders priced strictly better fill fully in time
   priority; orders at P* fill in time priority until volume is exhausted;
   same-tenant pairs are skipped during allocation (volume may then realize
   below `vol(P*)` — money moves only on realized fills, so conservation
   holds; documented simplification).
4. **Uniform price**: every fill, mint legs included, prints at P*. That is
   the whole point of the Budish paper and the whole point of Vickrey 1961.

**Settlement journals** (per fill; the quota-market shape, memo
`bourse:{item}`): buyer `market-clearing DEBIT / market-escrow CREDIT`
(notional); seller `treasury DEBIT (notional − fee)`, `market-fee DEBIT fee`
into `market-sales CREDIT notional`. `brp.exchange.fee-bips` (default 50)
is docs/03 §4's auction-commission knob. The mint settles identically under
the `mint` tenant; its margin accumulates in its treasury and the sweep
(§2) takes it daily.

**Batch execution** (`ExchangeClearingService.runBatch()`): one insert-only
guard row per `batch_seq` (`Persistable` isNew — the ProfileRun pattern),
then a single transaction per batch: pull resting orders per item, derive
mint quotes, clear, settle, crawl refs, record clears, feed CPI
(`MarketPriceService.record(..., "bourse", "clear")`), emit nothing else.
`@Scheduled(fixedDelayString + initialDelayString = ${brp.exchange.clear-ms:300000})`
— the P4 boot-ambush lesson, paid forward. Manual trigger endpoint for ops
and tests.

## 7. The bimetallic closure

`brp.peg.auto-observe` (default **true**): after each batch,
`minecraft:emerald` clears call `peg.observe("emerald", price/100)`; each
quota-market trade calls `peg.observe("quota", price/100)`. The ±5% band
stops waiting for hand-fed observations — the market phones it in, and the
existing machinery (streak ≥ 2 → REPEG_VOTE → OWNER ladder) does the rest.
No third governance channel was created in the making of this section.

## 8. The mod: catalog and warehouse

- **Catalog push** (P5-10): the agent enumerates registry item ids
  (`BuiltInRegistries.ITEM` keySet ◆ verify against 1.21.1 mappings),
  batches 500 ids per `POST /api/v1/exchange/catalog`, push-on-change and
  push-on-boot. Backend upserts display names only — listing state stays
  governance-owned.
- **Warehouse** (P5-11): bound container (`brp.vault.container`) is the
  physical counterpart. **Deposit**: agent scans, stages the rows in
  LocalStore, files the deposit, removes the goods on ack — goods leave the
  world before the books move, the P3 mint-ordering rule pointed the other
  way and for the same reason. **Withdraw**: file claim → backend debits
  free qty and returns the claim → agent dispenses → confirm; stale claims
  re-credit (SagaRecovery sweep). **Conservation**: every warehouse
  mutation writes an append-only movement row (`idem_key` unique) — the
  reconciliation desk (R-1) gets two identities for free: per item and
  tenant, `warehouse ≡ Σ movements`, and Σ `qty_held` ≡ resting ASK volume.

## 9. Task board

| # | Slice | Deliverable | Status |
|---|---|---|---|
| P5-01 | A | V22 migration, entities, repositories | ✓ |
| P5-02 | A | Catalog + listing governance (two presets) + push API | ✓ |
| P5-03 | B | Warehouse (deposit/withdraw/claims/movements, conservation) | ✓ |
| P5-04 | C | `AuctionMath` kink algorithm (pure) | ✓ |
| P5-05 | D | Order filing (escrow/hold, idem, cancel) | ✓ |
| P5-06 | E | Listed mint (quotes, seigniorage sweep) | ✓ |
| P5-07 | E | Batch clearing scheduler (single-tx, guard, initialDelay) | ✓ |
| P5-08 | E | Ref crawl + CPI feed + bimetallic closure (peg auto-observe) | ✓ |
| P5-09 | F | Mod: item catalog push | ✓ |
| P5-10 | F | Mod: warehouse deposit/withdraw via vault staging | ✓ |
| P5-11 | G | Portal bourse page + controller endpoints | ✓ |
| P5-12 | G | OpenAPI paths + messages (en/zh) | ✓ |
| P5-13 | G | Test-count sync (AGENTS.md, READMEs) | ✓ |
| P5-14 | H | Audit pass: shared-H2 scoping, scheduler races, idem replay | ✓ |

## 10. Configuration

| Key | Default | Meaning |
|---|---|---|
| `brp.exchange.preset` | `free` | `free` (FREE_BOURSE) / `curated` (CURATED_CATALOG) |
| `brp.exchange.clear-ms` | `300000` | batch cadence; also the scheduler initialDelay |
| `brp.exchange.fee-bips` | `50` | auction commission (docs/03 §4 knob) |
| `brp.exchange.mint-spread-bips` | `200` | full spread around ref (half each side) |
| `brp.exchange.mint-depth` | `64` | mint quote size per side per batch |
| `brp.exchange.mint-credit-cents` | `0` | how much the mint may bid beyond its float |
| `brp.exchange.mint-float-cents` | `0` | mint treasury working balance before the sweep |
| `brp.exchange.ref-crawl` | `0.25` | EWMA factor toward the clear |
| `brp.exchange.ref-min-qty` | `8` | minimum cleared qty for the crawl to move |
| `brp.exchange.order-ttl-days` | `7` | resting order expiry |
| `brp.peg.auto-observe` | `true` | clears feed the peg band |

## 11. Test plan

- `ListingPolicyTest` — both presets × three states × resolution matrix;
  catalog push upserts names without touching governance.
- `WarehouseTest` — deposit/withdraw/claims; conservation identity after
  mixed movement sequences; idem replay; claim release re-credits.
- `AuctionMathTest` — the kink table: max volume, imbalance tie, ref anchor
  (the 101/1000 case), mint participation, same-tenant skip, empty book.
- `BourseOrderTest` — BID escrow / ASK hold at filing, cancel releases,
  credit-RESTRICTED refused, idem replay.
- `ExchangeMintTest` — quotes respect reserve and depth; seigniorage sweep
  leaves the float, idem by date.
- `BourseClearingTest` — full batch: guard insert-only, single-tx
  settlement, unfilled persist, crawl moves only above min-qty, pinned
  never moves, CPI row lands, emerald clear feeds peg.observe.
- Shared-H2 discipline as P4: assertions scoped to own rows/tenants; peg
  assertions read the live band; Clock bean injected; global-table cleanup
  in `@AfterEach`.
- Mod units: catalog batching, staging/claim state machine.

## 12. Non-goals

- **Options on TPS stay permanently sealed** (docs/00). Futures shipped as
  P4 bonds; that was the whole satire allowance.
- No shorting (you sell what you hold), no margin, no borrowing.
- No continuous trading phase — the bourse clears in batches or it does not
  clear at all (R-8).
- No villager-telemetry pipe into the book (§5). The hall keeps its license;
  the bourse keeps its hands.
- No real money, ever (red line 7). The bourse trades items for play-money
  and the audit desk reads both sides of every joke.
