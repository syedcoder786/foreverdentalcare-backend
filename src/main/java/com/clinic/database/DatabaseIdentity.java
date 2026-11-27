package com.clinic.database;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells whether two connections reach the same clinic data, so the app never switches to (and copies data onto) the
 * database it is already using. Compares what identifies the data - server, database, user and clinic schema - instead
 * of raw JDBC URLs, which differ in parameter order, encoding and SSL settings for the same database.
 * <p>
 * A Supabase project is recognised by its project ref, so its direct host ({@code db.<ref>.supabase.co}) and its
 * poolers (user {@code postgres.<ref>}) count as the same database.
 */
final class DatabaseIdentity {

    private static final Pattern POSTGRES_URL =
            Pattern.compile("^jdbc:postgresql://(\\[[^\\]]+\\]|[^:/?]+)(?::(\\d+))?/([^?]*)(?:\\?(.*))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPABASE_DIRECT_HOST = Pattern.compile("^db\\.([a-z0-9]+)\\.supabase\\.co$");
    private static final Pattern SUPABASE_POOLER_USER = Pattern.compile("^[a-z0-9_]+\\.([a-z0-9]+)$");

    private DatabaseIdentity() {
    }

    static boolean same(String jdbcUrlA, String userA, String jdbcUrlB, String userB) {
        return of(jdbcUrlA, userA).equals(of(jdbcUrlB, userB));
    }

    /** A normalised key for the data a connection reaches. */
    static String of(String jdbcUrl, String username) {
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        Matcher m = POSTGRES_URL.matcher(jdbcUrl == null ? "" : jdbcUrl.trim());
        if (!m.matches()) {
            return "other:" + (jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(Locale.ROOT)) + "|" + user;
        }
        String host = m.group(1).replaceAll("^\\[(.*)]$", "$1").toLowerCase(Locale.ROOT);
        String port = m.group(2) == null ? "5432" : m.group(2);
        String database = decode(m.group(3));
        String schema = clinicSchema(m.group(4));

        String ref = supabaseRef(host, user);
        if (ref != null) {
            return "supabase:" + ref + "/" + database + "/" + schema;
        }
        return "postgres:" + host + ":" + port + "/" + database + "/" + user + "/" + schema;
    }

    private static String supabaseRef(String host, String user) {
        Matcher direct = SUPABASE_DIRECT_HOST.matcher(host);
        if (direct.matches()) {
            return direct.group(1);
        }
        if (host.endsWith(".pooler.supabase.com")) {
            Matcher pooler = SUPABASE_POOLER_USER.matcher(user);
            if (pooler.matches()) {
                return pooler.group(1);
            }
        }
        return null;
    }

    /** First entry of currentSchema (the schema that holds the clinic tables), or "public". */
    private static String clinicSchema(String query) {
        if (query != null) {
            for (String param : query.split("&")) {
                int eq = param.indexOf('=');
                if (eq > 0 && "currentschema".equalsIgnoreCase(param.substring(0, eq))) {
                    String first = decode(param.substring(eq + 1)).split(",")[0].trim().toLowerCase(Locale.ROOT);
                    return first.isEmpty() ? "public" : first;
                }
            }
        }
        return "public";
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
