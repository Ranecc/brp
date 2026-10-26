package dev.brp.mod.store;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Agent-local durable store port. SQLite behind the default implementation
 * (embedded, single file under <server>/brp/, journaled WAL mode).
 *
 * The CORE never sees JDBC or SQLite types — a swap to H2/LMDB later touches
 * only the impl. Transactions on this port map @Atomic semantics on the agent
 * side (docs/06 section 7).
 */
public interface LocalStore {

    void init() throws Exception;

    /** Run body inside one transaction: all-or-nothing. */
    <T> T inTransaction(Work<T> body) throws Exception;

    /** Upsert kv row (namespace + key unique). */
    void put(String ns, String key, String value);

    Optional<String> get(String ns, String key);

    /** Append to an append-only log table (agent metadata, replay cursors). */
    long append(String ns, String key, String value);

    @FunctionalInterface
    interface Work<T> {
        T run(Tx tx) throws Exception;
    }

    interface Tx {
        void exec(String sql, Object... args) throws Exception;
        java.util.List<String[]> query(String sql, Object... args) throws Exception;
    }
}
