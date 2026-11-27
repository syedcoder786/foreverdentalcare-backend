package com.clinic.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copies every clinic table from one PostgreSQL database to another that already has the same schema (both migrated
 * by Flyway to the same version). The target's existing rows are replaced. Runs in a single target transaction, so
 * on any error the target is left as it was. The source is read from one consistent snapshot.
 */
final class DatabaseCopier {

    record Result(int tables, long rows) {
    }

    private static final String HISTORY_TABLE = "flyway_schema_history";
    private static final int BATCH = 500;

    private DatabaseCopier() {
    }

    static Result copy(Connection source, Connection target) throws SQLException {
        String sourceSchema = currentSchema(source);
        String targetSchema = currentSchema(target);
        List<String> targetTables = tables(target, targetSchema);
        Set<String> sourceTables = new HashSet<>(tables(source, sourceSchema));
        List<String> order = insertOrder(targetTables, foreignKeys(target, targetSchema));

        source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        source.setAutoCommit(false);
        source.setReadOnly(true);
        target.setAutoCommit(false);
        try {
            if (!targetTables.isEmpty()) {
                try (Statement st = target.createStatement()) {
                    st.execute("TRUNCATE TABLE " + targetTables.stream().map(t -> qualified(targetSchema, t))
                            .collect(Collectors.joining(", ")) + " RESTART IDENTITY CASCADE");
                }
            }
            int copiedTables = 0;
            long rows = 0;
            for (String table : order) {
                if (sourceTables.contains(table)) {
                    rows += copyTable(source, sourceSchema, target, targetSchema, table);
                    copiedTables++;
                }
            }
            resetIdentitySequences(target, targetSchema);
            target.commit();
            return new Result(copiedTables, rows);
        } catch (SQLException | RuntimeException ex) {
            target.rollback();
            throw ex;
        } finally {
            source.rollback();
        }
    }

    private static long copyTable(Connection source, String sourceSchema, Connection target, String targetSchema,
                                  String table) throws SQLException {
        Map<String, Boolean> targetColumns = columns(target, targetSchema, table);
        Set<String> sourceColumns = columns(source, sourceSchema, table).keySet();
        List<String> cols = targetColumns.keySet().stream().filter(sourceColumns::contains).toList();
        if (cols.isEmpty()) {
            return 0;
        }
        boolean hasIdentity = cols.stream().anyMatch(targetColumns::get);
        String colList = cols.stream().map(DatabaseCopier::quote).collect(Collectors.joining(", "));
        List<String> pk = primaryKey(source, sourceSchema, table);
        String select = "SELECT " + colList + " FROM " + qualified(sourceSchema, table)
                + (pk.isEmpty() ? "" : " ORDER BY " + pk.stream().map(DatabaseCopier::quote).collect(Collectors.joining(", ")));
        String insert = "INSERT INTO " + qualified(targetSchema, table) + " (" + colList + ")"
                + (hasIdentity ? " OVERRIDING SYSTEM VALUE" : "")
                + " VALUES (" + String.join(", ", java.util.Collections.nCopies(cols.size(), "?")) + ")";

        long count = 0;
        try (PreparedStatement read = source.prepareStatement(select);
             PreparedStatement write = target.prepareStatement(insert)) {
            read.setFetchSize(BATCH);
            try (ResultSet rs = read.executeQuery()) {
                while (rs.next()) {
                    for (int i = 1; i <= cols.size(); i++) {
                        write.setObject(i, rs.getObject(i));
                    }
                    write.addBatch();
                    if (++count % BATCH == 0) {
                        write.executeBatch();
                    }
                }
            }
            if (count % BATCH != 0) {
                write.executeBatch();
            }
        } catch (SQLException ex) {
            throw new SQLException("Copying table " + table + " failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
        return count;
    }

    /** After inserting explicit ids, move each identity sequence past the highest id so new rows don't collide. */
    private static void resetIdentitySequences(Connection target, String schema) throws SQLException {
        List<String[]> identities = new ArrayList<>();
        try (PreparedStatement ps = target.prepareStatement("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = ? AND is_identity = 'YES'
                """)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    identities.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
        }
        for (String[] id : identities) {
            String table = qualified(schema, id[0]);
            try (PreparedStatement ps = target.prepareStatement(
                    "SELECT setval(pg_get_serial_sequence(?, ?), COALESCE((SELECT MAX(" + quote(id[1]) + ") FROM "
                            + table + "), 0) + 1, false)")) {
                ps.setString(1, table);
                ps.setString(2, id[1]);
                ps.execute();
            }
        }
    }

    /** Parents before children, so foreign keys are satisfied while inserting. Self-references are ignored. */
    static List<String> insertOrder(List<String> tables, Map<String, Set<String>> parentsByChild) {
        Map<String, Set<String>> remaining = new LinkedHashMap<>();
        for (String t : tables) {
            Set<String> parents = new HashSet<>(parentsByChild.getOrDefault(t, Set.of()));
            parents.remove(t);
            parents.retainAll(tables);
            remaining.put(t, parents);
        }
        List<String> order = new ArrayList<>();
        Deque<String> ready = new ArrayDeque<>();
        remaining.forEach((t, p) -> {
            if (p.isEmpty()) {
                ready.add(t);
            }
        });
        while (!ready.isEmpty()) {
            String t = ready.poll();
            if (remaining.remove(t) == null) {
                continue;
            }
            order.add(t);
            remaining.forEach((child, parents) -> {
                if (parents.remove(t) && parents.isEmpty()) {
                    ready.add(child);
                }
            });
        }
        order.addAll(remaining.keySet()); // a foreign key cycle: insert in any order and let the database decide
        return order;
    }

    private static String currentSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT current_schema()")) {
            rs.next();
            String schema = rs.getString(1);
            if (schema == null) {
                throw new SQLException("The database has no current schema (check the schema name)");
            }
            return schema;
        }
    }

    private static List<String> tables(Connection c, String schema) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = ? AND table_type = 'BASE TABLE' AND table_name <> ?
                ORDER BY table_name
                """)) {
            ps.setString(1, schema);
            ps.setString(2, HISTORY_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }

    /** Column name -> is identity column, in table order. Generated columns are skipped (they can't be written). */
    private static Map<String, Boolean> columns(Connection c, String schema, String table) throws SQLException {
        Map<String, Boolean> result = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT column_name, is_identity FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND COALESCE(is_generated, 'NEVER') = 'NEVER'
                ORDER BY ordinal_position
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), "YES".equals(rs.getString(2)));
                }
            }
        }
        return result;
    }

    private static List<String> primaryKey(Connection c, String schema, String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getPrimaryKeys(null, schema, table)) {
            Map<Short, String> bySeq = new java.util.TreeMap<>();
            while (rs.next()) {
                bySeq.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
            result.addAll(bySeq.values());
        }
        return result;
    }

    private static Map<String, Set<String>> foreignKeys(Connection c, String schema) throws SQLException {
        Map<String, Set<String>> result = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT child.relname, parent.relname
                FROM pg_constraint con
                JOIN pg_class child ON child.oid = con.conrelid
                JOIN pg_class parent ON parent.oid = con.confrelid
                JOIN pg_namespace n ON n.oid = child.relnamespace
                WHERE con.contype = 'f' AND n.nspname = ?
                """)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>()).add(rs.getString(2));
                }
            }
        }
        return result;
    }

    private static String qualified(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
