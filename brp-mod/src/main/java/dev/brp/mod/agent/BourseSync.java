package dev.brp.mod.agent;

import dev.brp.mod.client.BrpServerApi;
import dev.brp.mod.store.LocalStore;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The intake desk state machine (docs/14 §8, P5-10). The bound container is
 * a deposit slot: whatever the operator adds to it leaves the world BEFORE
 * the books move, staged durably in LocalStore so a crash never invents or
 * erases custody. Withdraw claims come from the backend (source of truth);
 * goods spawn first, the claim confirms after.
 *
 * Ordering rules (the mint-ordering lesson, deposit direction):
 *   stage -> extract -> file -> ack clears staging
 * and every file carries the idem key fixed at staging time, so a retried
 * deposit replays instead of doubling.
 */
public final class BourseSync {

    static final String NS_STAGE = "bourse-stage";
    static final String NS_BASELINE = "bourse-baseline";
    static final String NS_PICKUP = "bourse-pickup";

    public record Summary(int staged, int filed, int confirmed, int released, int drift) {}

    private final BrpServerApi api;
    private final LocalStore store;
    private final BoursePort port;
    private final String tenantId;
    private final Consumer<String> log;

    public BourseSync(BrpServerApi api, LocalStore store, BoursePort port,
                      String tenantId, Consumer<String> log) {
        this.api = api;
        this.store = store;
        this.port = port;
        this.tenantId = tenantId;
        this.log = log;
    }

    /** One pass over the three duties. Never throws — a desk that crashes mid-pass lies. */
    public Summary sync() {
        int filed = fileStagedDeposits();
        int staged = noticeNewArrivals();
        var claims = settleWithdrawClaims();
        return new Summary(staged, filed, claims.confirmed(), claims.released(), claims.drift());
    }

    /** Staged rows that have not been acked keep trying — the idem key replays. */
    int fileStagedDeposits() {
        int filed = 0;
        for (String[] row : rows(NS_STAGE)) {
            String idemKey = row[0];
            int sep = row[1].indexOf('|');
            String itemId = row[1].substring(0, sep);
            long qty = Long.parseLong(row[1].substring(sep + 1));
            try {
                api.postDeposit(tenantId, new BrpServerApi.WarehouseDeposit(itemId, qty, idemKey)).join();
                inTx(tx -> tx.exec("delete from kv where ns = ? and key = ?", NS_STAGE, idemKey));
                filed++;
                log.accept("[BRP] bourse deposit filed: " + qty + "x " + itemId);
            } catch (RuntimeException e) {
                log.accept("[BRP] bourse deposit deferred (" + itemId + "): " + e.getMessage());
            }
        }
        return filed;
    }

    /** Container vs baseline: anything new on the shelf gets staged, extracted, filed. */
    int noticeNewArrivals() {
        SortedMap<String, Long> actual = port.inventory();
        Map<String, Long> baseline = baseline();
        int staged = 0;
        for (Map.Entry<String, Long> e : actual.entrySet()) {
            long expected = baseline.getOrDefault(e.getKey(), 0L);
            long delta = e.getValue() - expected;
            if (delta <= 0) {
                continue;
            }
            String idemKey = "bourse-dep:" + tenantId + ":" + UUID.randomUUID();
            // goods leave the world first, books second; after the extract the
            // shelf sits exactly at the baseline again, so the baseline row
            // never moves on the deposit path (the real-net boot caught this:
            // a stale pre-extract baseline made every later arrival read as
            // a shortfall)
            if (!port.extract(e.getKey(), delta)) {
                log.accept("[BRP] bourse intake failed to extract " + delta + "x " + e.getKey());
                continue;
            }
            inTx(tx -> {
                tx.exec("insert or replace into kv (ns, key, value) values (?, ?, ?)",
                        NS_STAGE, idemKey, e.getKey() + "|" + delta);
            });
            staged++;
        }
        return staged;
    }

    record Settled(int confirmed, int released, int drift) {}

    Settled settleWithdrawClaims() {
        int confirmed = 0;
        int released = 0;
        try {
            for (BrpServerApi.ClaimRow claim : api.getPendingClaims(tenantId).join()) {
                // the books were debited when the claim was filed; the desk
                // spawns the goods into the container and confirms after.
                // The real-net boot caught this calling the inverse port
                // method: extracted goods AND debited books, custody vanished.
                if (port.deposit(claim.itemId(), claim.qty())) {
                    api.confirmClaim(tenantId, claim.id()).join();
                    adjustBaseline(claim.itemId(), claim.qty());
                    adjustPickup(claim.itemId(), claim.qty());
                    confirmed++;
                } else {
                    api.releaseClaim(tenantId, claim.id()).join();
                    released++;
                }
            }
        } catch (RuntimeException e) {
            log.accept("[BRP] bourse claims poll failed: " + e.getMessage());
            return new Settled(confirmed, released, 0);
        }
        int drift = 0;
        Map<String, Long> baseline = baseline();
        SortedMap<String, Long> actual = port.inventory();
        // watch the union: an item pocketed down to zero is gone from the
        // shelf but still expected — skipping it would leave a phantom
        // baseline that under-counts the next real arrival
        Set<String> watched = new LinkedHashSet<>(baseline.keySet());
        watched.addAll(actual.keySet());
        for (String itemId : watched) {
            long expected = baseline.getOrDefault(itemId, 0L);
            long have = actual.getOrDefault(itemId, 0L);
            if (have < expected) {
                long shortfall = expected - have;
                long pickup = pickupBalance(itemId);
                if (pickup > 0) {
                    // a confirmed payout waiting in the container is pocketed
                    // by hand — pocketing it is the point, not a theft
                    long absorbed = Math.min(pickup, shortfall);
                    adjustBaseline(itemId, -absorbed);
                    adjustPickup(itemId, -absorbed);
                    shortfall -= absorbed;
                }
                if (shortfall > 0) {
                    drift++;
                    log.accept("[BRP] bourse intake SHORT by " + shortfall
                            + "x " + itemId + " — nobody filed that withdrawal");
                }
            }
        }
        return new Settled(confirmed, released, drift);
    }

    private Map<String, Long> baseline() {
        Map<String, Long> out = new HashMap<>();
        for (String[] row : rows(NS_BASELINE)) {
            out.put(row[0], Long.parseLong(row[1]));
        }
        return out;
    }

    private void adjustBaseline(String itemId, long delta) {
        long current = baseline().getOrDefault(itemId, 0L);
        inTx(tx -> tx.exec("insert or replace into kv (ns, key, value) values (?, ?, ?)",
                NS_BASELINE, itemId, String.valueOf(current + delta)));
    }

    /** Spawned-but-not-yet-pocketed payout qty per item; absorbs lawful pickup. */
    private long pickupBalance(String itemId) {
        return rows(NS_PICKUP).stream()
                .filter(row -> row[0].equals(itemId))
                .mapToLong(row -> Long.parseLong(row[1]))
                .findFirst().orElse(0L);
    }

    private void adjustPickup(String itemId, long delta) {
        long current = pickupBalance(itemId);
        inTx(tx -> tx.exec("insert or replace into kv (ns, key, value) values (?, ?, ?)",
                NS_PICKUP, itemId, String.valueOf(current + delta)));
    }

    @FunctionalInterface
    private interface TxBody {
        void run(LocalStore.Tx tx) throws Exception;
    }

    private void inTx(TxBody body) {
        try {
            store.inTransaction(tx -> {
                body.run(tx);
                return null;
            });
        } catch (Exception e) {
            throw new IllegalStateException("bourse staging write failed", e);
        }
    }

    private java.util.List<String[]> rows(String ns) {
        try {
            return store.inTransaction(tx -> tx.query(
                    "select key, value from kv where ns = ? order by key", ns));
        } catch (Exception e) {
            log.accept("[BRP] local store read failed: " + e.getMessage());
            return java.util.List.of();
        }
    }
}
