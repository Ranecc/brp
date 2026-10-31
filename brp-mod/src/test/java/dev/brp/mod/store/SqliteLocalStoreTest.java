package dev.brp.mod.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The only unit that opens a real SQLite file — the real-net boot shipped a
 * mod whose intake desk died on "no suitable driver" because every other
 * store test ran against the fake. This one exercises the actual jarJar
 * driver path: warm-up, WAL pragmas, kv upserts, append-only logs and the
 * all-or-nothing transaction boundary.
 */
class SqliteLocalStoreTest {

    @TempDir
    Path dir;

    private SqliteLocalStore fresh() throws Exception {
        SqliteLocalStore store = new SqliteLocalStore(dir.resolve("bourse.db"));
        store.init();
        return store;
    }

    @Test
    void initTwiceOnTheSameFileIsIdempotent() throws Exception {
        SqliteLocalStore store = fresh();
        store.put("bourse", "baseline", "16");
        // a second store on the same file (the restart path) sees committed data
        SqliteLocalStore reopened = fresh();
        assertEquals("16", reopened.get("bourse", "baseline").orElse("?"));
    }

    @Test
    void kvUpsertAndAppendBehave() throws Exception {
        SqliteLocalStore store = fresh();
        store.put("ns", "k", "v1");
        store.put("ns", "k", "v2"); // upsert, not a duplicate row
        assertEquals("v2", store.get("ns", "k").orElse("?"));
        assertTrue(store.get("other", "k").isEmpty());

        long first = store.append("log", "cursor", "a");
        long second = store.append("log", "cursor", "b");
        assertTrue(second > first, "append-only ids must grow");
    }

    @Test
    void transactionIsAllOrNothing() throws Exception {
        SqliteLocalStore store = fresh();
        assertThrows(Exception.class, () -> store.inTransaction(tx -> {
            tx.exec("INSERT INTO kv(ns,key,value) VALUES(?,?,?)", "ns", "kept", "1");
            tx.exec("INSERT INTO kv(ns,key,value) VALUES(?,?,?)", "ns", "kept", "2");
            tx.exec("INSERT INTO nonexistent VALUES(?)", "boom");
            return null;
        }));
        List<String[]> rows = store.inTransaction(tx ->
                tx.query("SELECT value FROM kv WHERE ns='ns'"));
        assertTrue(rows.isEmpty(), "a failed transaction must leave no rows");
    }
}
