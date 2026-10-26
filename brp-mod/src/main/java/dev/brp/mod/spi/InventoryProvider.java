package dev.brp.mod.spi;

import java.util.List;
import java.util.Map;

/**
 * Unified inventory abstraction over vanilla containers, AE2 ME networks and
 * Create item vaults (WMS plane, docs/02-integrations.md section 2-3).
 *
 * All mutations are idempotent via transferId; adapters must implement
 * "same transferId returns the first result" semantics.
 */
public interface InventoryProvider extends BrpAdapter {

    /** Stable identity of the backing container (pos + type + dimension hash). */
    String containerId();

    record ItemAmount(String itemId, long amount) {}

    /** Incremental stock report since the given cursor (epoch seq, 0 = full). */
    record StockReport(long cursor, List<ItemAmount> stock, boolean full) {}

    StockReport snapshot(long sinceCursor);

    record TransferCmd(String transferId, String itemId, long amount, Direction direction) {
        public enum Direction { INBOUND, OUTBOUND }
    }

    /** amountApplied lets partial fills report honestly; retries with the same id must not re-apply. */
    record TransferResult(String transferId, long amountApplied, boolean success, String message) {}

    TransferResult transfer(TransferCmd cmd);

    /** Convenience for policy checks: current on-hand of one item. */
    default long onHand(String itemId) {
        return snapshot(0).stock().stream()
                .filter(a -> a.itemId().equals(itemId))
                .mapToLong(ItemAmount::amount).findFirst().orElse(0);
    }

    static Map<String, Long> asMap(StockReport report) {
        return report.stock().stream()
                .collect(java.util.stream.Collectors.toMap(ItemAmount::itemId, ItemAmount::amount));
    }
}
