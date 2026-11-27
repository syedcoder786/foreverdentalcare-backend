package com.clinic.database;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A PostgreSQL connection chosen in Admin -> Database (for example a Supabase project).
 *
 * @param schema  schema that holds the clinic tables. Created on first use. Blank means the server default
 *                ({@code public}). A dedicated schema keeps the tables out of Supabase's public Data API.
 * @param sslMode PostgreSQL JDBC {@code sslmode}: disable, allow, prefer, require, verify-ca or verify-full.
 */
public record DatabaseConnection(
        String host,
        int port,
        String database,
        String username,
        String password,
        String schema,
        String sslMode
) {

    /** Supabase's transaction pooler: it cannot keep server-side prepared statements between transactions. */
    private static final int SUPABASE_TRANSACTION_POOLER_PORT = 6543;

    public boolean hasSchema() {
        return schema != null && !schema.isBlank() && !"public".equals(schema);
    }

    public String jdbcUrl() {
        String hostPart = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host; // IPv6 literal
        List<String> params = new ArrayList<>();
        params.add("sslmode=" + enc(sslMode == null || sslMode.isBlank() ? "prefer" : sslMode));
        if (hasSchema()) {
            // Clinic schema first; "extensions" is where Supabase installs extensions such as btree_gist.
            params.add("currentSchema=" + enc(schema + ",extensions,public"));
        }
        if (port == SUPABASE_TRANSACTION_POOLER_PORT) {
            params.add("prepareThreshold=0");
        }
        params.add("connectTimeout=15");
        params.add("ApplicationName=" + enc("dental-clinic"));
        return "jdbc:postgresql://" + hostPart + ":" + port + "/" + enc(database) + "?" + String.join("&", params);
    }

    /** Human-readable summary without the password, for logs and the audit trail. */
    public String describe() {
        return username + "@" + host + ":" + port + "/" + database + (hasSchema() ? " (schema " + schema + ")" : "");
    }

    public DatabaseConnection withPassword(String newPassword) {
        return new DatabaseConnection(host, port, database, username, newPassword, schema, sslMode);
    }

    @Override
    public String toString() {
        return "DatabaseConnection[" + describe() + "]"; // never print the password
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
