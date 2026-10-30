package dev.brp.mod.agent;

import dev.brp.mod.client.BrpServerApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The item catalog push (docs/14 §8, P5-09): every registered item id the
 * server knows about, batched 500 per call, pushed on boot and on change.
 * The backend may rename; it may never list — governance stays the owner's.
 */
public final class ItemCatalog {

    static final int BATCH = 500;

    private final Supplier<List<String>> registryIds;
    private final BrpServerApi api;
    private final Consumer<String> log;
    private String fingerprint = "";
    private boolean everPushed;

    public ItemCatalog(Supplier<List<String>> registryIds, BrpServerApi api,
                       Consumer<String> log) {
        this.registryIds = registryIds;
        this.api = api;
        this.log = log;
    }

    /** One pass: no-op when the registry has not changed since the last push. */
    public synchronized void push() {
        List<String> ids;
        try {
            ids = registryIds.get();
        } catch (RuntimeException e) {
            log.accept("[BRP] catalog read failed: " + e.getMessage());
            return;
        }
        if (ids == null || ids.isEmpty()) {
            return;
        }
        TreeSet<String> sorted = new TreeSet<>(ids);
        String fp = String.valueOf(sorted.hashCode()) + ':' + sorted.size();
        if (everPushed && fp.equals(fingerprint)) {
            return;
        }
        for (List<String> batch : batches(new ArrayList<>(sorted))) {
            List<Map<String, String>> body = batch.stream()
                    .map(id -> Map.of("itemId", id))
                    .toList();
            api.postCatalog(body)
                    .thenAccept(v -> log.accept("[BRP] catalog batch pushed: " + batch.size()))
                    .exceptionally(t -> {
                        log.accept("[BRP] catalog push failed: " + t.getMessage());
                        return null;
                    });
        }
        fingerprint = fp;
        everPushed = true;
    }

    static List<List<String>> batches(List<String> ids) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += BATCH) {
            out.add(ids.subList(i, Math.min(i + BATCH, ids.size())));
        }
        return out;
    }
}
