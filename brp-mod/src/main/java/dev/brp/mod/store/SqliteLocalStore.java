package dev.brp.mod.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Embedded SQLite LocalStore. Ships via jar-in-jar (org.xerial:sqlite-jdbc,
 * bundled natives) — see build.gradle.ksl comments; nothing shades into the
 * brp-common annotation namespace.
 *
 * WAL journaling + single-connection serialized access keeps SQLite's
 * file lock sane under the agent's single writer discipline.
 */
public final class SqliteLocalStore implements LocalStore {

    private final Path dbFile;
    private Connection conn;

    public SqliteLocalStore(Path dbFile) {
        this.dbFile = dbFile;
    }

    @Override
    public synchronized void init() throws Exception {
        Files.createDirectories(dbFile.getParent());
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        exec("PRAGMA journal_mode=WAL");
        exec("PRAGMA synchronous=NORMAL");
        exec("CREATE TABLE IF NOT EXISTS kv (ns TEXT NOT NULL, key TEXT NOT NULL,"
                + " value TEXT, PRIMARY KEY (ns, key))");
        exec("CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " ns TEXT NOT NULL, key TEXT, value TEXT, ts TEXT DEFAULT"
                + " (strftime('%Y-%m-%dT%H:%M:%fZ','now')))");
    }

    @Override
    public synchronized <T> T inTransaction(Work<T> body) throws Exception {
        boolean previous = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            T result = body.run(new SqlTx());
            conn.commit();
            return result;
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previous);
        }
    }

    @Override
    public synchronized void put(String ns, String key, String value) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO kv(ns,key,value) VALUES(?,?,?)"
                            + " ON CONFLICT(ns,key) DO UPDATE SET value=excluded.value");
            ps.setString(1, ns);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("kv put failed: " + ns + "/" + key, e);
        }
    }

    @Override
    public synchronized Optional<String> get(String ns, String key) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT value FROM kv WHERE ns=? AND key=?");
            ps.setString(1, ns);
            ps.setString(2, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("kv get failed: " + ns + "/" + key, e);
        }
    }

    @Override
    public synchronized long append(String ns, String key, String value) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO logs(ns,key,value) VALUES(?,?,?)");
            ps.setString(1, ns);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
            PreparedStatement last = conn.prepareStatement(
                    "SELECT last_insert_rowid()");
            ResultSet rs = last.executeQuery();
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("log append failed: " + ns, e);
        }
    }

    private void exec(String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private final class SqlTx implements Tx {
        @Override public void exec(String sql, Object... args) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bind(ps, args);
                ps.executeUpdate();
            }
        }
        @Override public List<String[]> query(String sql, Object... args) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    int cols = rs.getMetaData().getColumnCount();
                    List<String[]> rows = new ArrayList<>();
                    while (rs.next()) {
                        String[] row = new String[cols];
                        for (int i = 0; i < cols; i++) {
                            row[i] = rs.getString(i + 1);
                        }
                        rows.add(row);
                    }
                    return rows;
                }
            }
        }
        private void bind(PreparedStatement ps, Object[] args) throws SQLException {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
        }
    }
}
