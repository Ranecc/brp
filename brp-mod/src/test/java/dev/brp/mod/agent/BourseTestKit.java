package dev.brp.mod.agent;

import dev.brp.mod.client.BrpServerApi;
import dev.brp.mod.store.LocalStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Test doubles for the bourse desk: a fake API that records what flew, a
 * fake port whose shelf the tests stock, and a fake store that understands
 * exactly the three statements BourseSync speaks — nothing more.
 */
final class BourseTestKit {

    private BourseTestKit() {}

    static final class TestApi implements BrpServerApi {
        final List<List<Map<String, String>>> catalogBatches = new ArrayList<>();
        final List<WarehouseDeposit> deposits = new ArrayList<>();
        List<ClaimRow> pendingClaims = List.of();
        final List<Long> confirmed = new ArrayList<>();
        final List<Long> released = new ArrayList<>();
        boolean failDeposits;

        @Override
        public CompletableFuture<Map<String, Object>> postCatalog(List<Map<String, String>> items) {
            catalogBatches.add(items);
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletableFuture<Map<String, Object>> postDeposit(String tenantId, WarehouseDeposit deposit) {
            if (failDeposits) {
                return CompletableFuture.failedFuture(new RuntimeException("backend down"));
            }
            deposits.add(deposit);
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletableFuture<List<ClaimRow>> getPendingClaims(String tenantId) {
            return CompletableFuture.completedFuture(pendingClaims);
        }

        @Override
        public CompletableFuture<Void> confirmClaim(String tenantId, long claimId) {
            confirmed.add(claimId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> releaseClaim(String tenantId, long claimId) {
            released.add(claimId);
            return CompletableFuture.completedFuture(null);
        }

        // --- unused plane ---

        @Override public CompletableFuture<TelemetryAck> postTelemetry(List<EventDto> events) { throw unused(); }
        @Override public CompletableFuture<List<GovernorVerdict>> getGovernor(String tenantId) { throw unused(); }
        @Override public CompletableFuture<List<ChunkSlot>> getChunks(String tenantId) { throw unused(); }
        @Override public CompletableFuture<List<FormRow>> getForms(String tenantId) { throw unused(); }
        @Override public CompletableFuture<ApsAllocation> getApsAllocation(String tenantId) { throw unused(); }
        @Override public CompletableFuture<Map<String, Object>> postJournal(String tenantId, PostJournal request) { throw unused(); }
        @Override public CompletableFuture<Map<String, Object>> putInventorySnapshots(String tenantId, String batchMode, List<Map<String, Object>> lines) { throw unused(); }
        @Override public CompletableFuture<Map<String, Object>> postPlayers(String batchId, List<PlayerRef> players) { throw unused(); }
        @Override public CompletableFuture<SignalPoll> pollSignals(String tenantId) { throw unused(); }
        @Override public CompletableFuture<Void> ackSignals(String tenantId, List<Long> ids) { throw unused(); }

        private UnsupportedOperationException unused() {
            return new UnsupportedOperationException("not part of this story");
        }
    }

    static final class FakePort implements BoursePort {
        final SortedMap<String, Long> contents = new TreeMap<>();
        boolean failExtract;
        boolean failSpawn;

        @Override
        public SortedMap<String, Long> inventory() {
            return new TreeMap<>(contents);
        }

        @Override
        public boolean extract(String itemId, long qty) {
            if (failExtract) {
                return false;
            }
            long have = contents.getOrDefault(itemId, 0L);
            if (have < qty) {
                return false;
            }
            if (have == qty) {
                contents.remove(itemId);
            } else {
                contents.put(itemId, have - qty);
            }
            return true;
        }

        @Override
        public boolean deposit(String itemId, long qty) {
            if (failSpawn) {
                return false;
            }
            contents.merge(itemId, qty, Long::sum);
            return true;
        }
    }

    static final class FakeStore implements LocalStore {
        final Map<String, Map<String, String>> tables = new HashMap<>();

        @Override public void init() {}

        @Override
        public <T> T inTransaction(Work<T> body) throws Exception {
            return body.run(new Tx() {
                @Override
                public void exec(String sql, Object... args) {
                    String s = sql.toLowerCase();
                    if (s.startsWith("insert or replace into kv")) {
                        tables.computeIfAbsent((String) args[0], k -> new HashMap<>())
                                .put((String) args[1], (String) args[2]);
                    } else if (s.startsWith("delete from kv")) {
                        tables.getOrDefault((String) args[0], new HashMap<>())
                                .remove((String) args[1]);
                    } else {
                        throw new UnsupportedOperationException(sql);
                    }
                }

                @Override
                public List<String[]> query(String sql, Object... args) {
                    String s = sql.toLowerCase();
                    if (s.startsWith("select key, value from kv")) {
                        return tables.getOrDefault((String) args[0], new HashMap<>()).entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(e -> new String[]{e.getKey(), e.getValue()})
                                .toList();
                    }
                    throw new UnsupportedOperationException(sql);
                }
            });
        }

        @Override
        public void put(String ns, String key, String value) {
            tables.computeIfAbsent(ns, k -> new HashMap<>()).put(key, value);
        }

        @Override
        public Optional<String> get(String ns, String key) {
            return Optional.ofNullable(tables.getOrDefault(ns, new HashMap<>()).get(key));
        }

        @Override
        public long append(String ns, String key, String value) {
            put(ns, key, value);
            return 0;
        }
    }
}
