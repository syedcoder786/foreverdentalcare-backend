package com.clinic.database;

import com.clinic.database.DatabaseDtos.ConnectionRequest;
import com.clinic.database.DatabaseDtos.StatusResponse;
import com.clinic.database.DatabaseDtos.SwitchRequest;
import com.clinic.database.DatabaseDtos.SwitchResponse;
import com.clinic.database.DatabaseDtos.TestResponse;
import com.clinic.database.DatabaseDtos.UseEnvironmentRequest;
import com.clinic.entity.Admin;
import com.clinic.exception.BusinessException;
import com.clinic.repository.AdminRepository;
import com.clinic.security.AdminPrincipal;
import com.clinic.service.AuditService;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.postgresql.util.PSQLException;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Admin -> Database: shows the active connection, tests a new one and switches the running app to it.
 * <p>
 * A switch never touches the current database. It opens the new one, brings its schema up to date with Flyway,
 * optionally copies all data across, saves the connection to the config file and only then routes new requests to it.
 * If any step fails, the app keeps using the current database.
 */
@Slf4j
@Service
public class DatabaseSwitchService {

    /** How long requests that started on the old database get to finish before its pool is closed. */
    private static final long RETIRE_AFTER_SECONDS = 30;
    private static final Pattern SUPABASE_DIRECT_HOST = Pattern.compile("^db\\.[a-z0-9]+\\.supabase\\.co$", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PASSWORD = Pattern.compile("(?i)(password=)[^&;]*");

    private final SwitchableDataSource dataSource;
    private final DatabaseConnectionStore store;
    private final DatabaseProperties properties;
    private final DataSourceProperties envProperties;
    private final FlywayProperties flywayProperties;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ScheduledExecutorService retirer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "db-pool-retirer");
        t.setDaemon(true);
        return t;
    });

    public DatabaseSwitchService(SwitchableDataSource dataSource, DatabaseConnectionStore store, DatabaseProperties properties,
                                 DataSourceProperties envProperties, FlywayProperties flywayProperties,
                                 AdminRepository adminRepository, PasswordEncoder passwordEncoder, AuditService audit) {
        this.dataSource = dataSource;
        this.store = store;
        this.properties = properties;
        this.envProperties = envProperties;
        this.flywayProperties = flywayProperties;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @PreDestroy
    void shutdown() {
        retirer.shutdownNow();
    }

    // ------------------------------------------------------------------ status

    public StatusResponse status() {
        SwitchableDataSource.Active a = dataSource.active();
        DatabaseConnection c = a.connection();
        boolean connected = false;
        String error = null;
        String serverVersion = null;
        String currentSchema = null;
        String schemaVersion = null;
        try (Connection con = dataSource.getConnection()) {
            connected = true;
            serverVersion = queryString(con, "SHOW server_version");
            currentSchema = queryString(con, "SELECT current_schema()");
            schemaVersion = queryString(con, "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL "
                    + "ORDER BY installed_rank DESC LIMIT 1");
        } catch (SQLException ex) {
            error = friendlyError(ex, c != null ? c.host() : null);
        }
        HikariPoolMXBean pool = a.pool().getHikariPoolMXBean();
        DatabaseConnectionStore.Saved saved = null;
        try {
            saved = store.load().orElse(null);
        } catch (IllegalStateException ex) {
            log.warn(ex.getMessage());
        }
        return new StatusResponse(
                a.source().name(),
                c != null ? c.host() : null,
                c != null ? c.port() : null,
                c != null ? c.database() : null,
                a.username(),
                c != null ? c.schema() : null,
                c != null ? c.sslMode() : null,
                redact(a.jdbcUrl()),
                connected, error, serverVersion, currentSchema, schemaVersion,
                pool != null ? pool.getActiveConnections() : null,
                pool != null ? pool.getTotalConnections() : null,
                a.pool().getMaximumPoolSize(),
                store.file().toString(),
                saved != null ? saved.savedAt() : null,
                saved != null ? saved.savedBy() : null,
                redact(envProperties.determineUrl()));
    }

    // ------------------------------------------------------------------ test

    public TestResponse test(ConnectionRequest request) {
        DatabaseConnection target = resolve(request);
        String schema = target.hasSchema() ? target.schema() : "public";
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        DriverManagerDataSource ds = new DriverManagerDataSource(target.jdbcUrl(), target.username(), target.password());
        try (Connection con = ds.getConnection()) {
            String version = queryString(con, "SHOW server_version");
            String database = queryString(con, "SELECT current_database()");
            boolean schemaExists = exists(con, "SELECT 1 FROM pg_namespace WHERE nspname = ?", schema);
            boolean hasHistory = exists(con, "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = 'flyway_schema_history'", schema);
            long otherTables = count(con, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                    + "AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'", schema);

            String schemaVersion = null;
            Long admins = null;
            Long patients = null;
            Long appointments = null;
            Long doctors = null;
            if (hasHistory) {
                String q = DatabaseCopier.quote(schema) + ".";
                schemaVersion = queryString(con, "SELECT version FROM " + q + "flyway_schema_history WHERE success AND version IS NOT NULL "
                        + "ORDER BY installed_rank DESC LIMIT 1");
                admins = countOrNull(con, "SELECT COUNT(*) FROM " + q + "admins");
                patients = countOrNull(con, "SELECT COUNT(*) FROM " + q + "patients");
                appointments = countOrNull(con, "SELECT COUNT(*) FROM " + q + "appointments");
                doctors = countOrNull(con, "SELECT COUNT(*) FROM " + q + "doctors");
            } else if (otherTables > 0) {
                errors.add("The schema \"" + schema + "\" already has " + otherTables + " table(s) that were not created by this app. "
                        + "Enter a different schema name, for example dental_clinic.");
            }

            int pending = 0;
            try {
                pending = flyway(ds, target.hasSchema() ? schema : null).info().pending().length;
            } catch (FlywayException ex) {
                warnings.add("Could not check the database schema: " + rootMessage(ex));
            }

            boolean same = isCurrent(target.jdbcUrl(), target.username());
            if (same) {
                errors.add("The app is already using this database.");
            }
            if ((patients != null && patients > 0) || (appointments != null && appointments > 0)) {
                warnings.add("This database already has clinic data (" + patients + " patients, " + appointments
                        + " appointments). Copying data replaces it; not copying keeps it and the app will show it.");
            }
            if (SUPABASE_DIRECT_HOST.matcher(target.host()).matches()) {
                warnings.add("Supabase direct connections work only over IPv6. This server can reach it now. If you move the "
                        + "backend to a host without IPv6, use the Session pooler connection string instead.");
            }
            if ("disable".equals(target.sslMode()) && !isLocal(target.host())) {
                warnings.add("SSL is off, so data travels unencrypted. Use \"require\" for a database on the internet.");
            }
            return new TestResponse(version, database, !schemaExists && target.hasSchema(), schemaVersion, pending,
                    admins, patients, appointments, doctors, errors, warnings, same);
        } catch (SQLException ex) {
            throw new BusinessException(friendlyError(ex, target.host()));
        }
    }

    // ------------------------------------------------------------------ switch

    public synchronized SwitchResponse switchTo(SwitchRequest request, AdminPrincipal principal) {
        Admin admin = verifyPassword(principal, request.currentPassword());
        DatabaseConnection target = resolve(request.connection());
        if (isCurrent(target.jdbcUrl(), target.username())) {
            throw new BusinessException("The app is already using this database");
        }
        SwitchableDataSource.Active next;
        try {
            next = DatabaseConfig.adminPanelPool(target, properties.poolSize(), true);
        } catch (RuntimeException ex) {
            throw new BusinessException(friendlyError(ex, target.host()));
        }
        return activate(next, request.copyData(), admin, () -> store.save(target, principal.email()),
                "Switched database to " + target.describe());
    }

    public synchronized SwitchResponse useEnvironment(UseEnvironmentRequest request, AdminPrincipal principal) {
        Admin admin = verifyPassword(principal, request.currentPassword());
        if (dataSource.active().source() == SwitchableDataSource.Source.ENVIRONMENT) {
            throw new BusinessException("The app is already using the database from backend/.env");
        }
        SwitchableDataSource.Active next = DatabaseConfig.environmentPool(envProperties, properties);
        try (Connection ignored = next.pool().getConnection()) {
            // connection works
        } catch (SQLException | RuntimeException ex) {
            next.pool().close();
            throw new BusinessException("Could not connect to the database in backend/.env: " + friendlyError(ex, null));
        }
        return activate(next, request.copyData(), admin, store::delete,
                "Switched database back to backend/.env (" + redact(next.jdbcUrl()) + ")");
    }

    private SwitchResponse activate(SwitchableDataSource.Active next, boolean copyData, Admin admin, Runnable persist,
                                    String summary) {
        boolean swapped = false;
        try {
            flyway(next.pool(), next.schema()).migrate();

            DatabaseCopier.Result copied = null;
            String accountNote = "";
            if (copyData) {
                try (Connection source = dataSource.getConnection(); Connection target = next.pool().getConnection()) {
                    copied = DatabaseCopier.copy(source, target);
                }
            } else {
                accountNote = ensureAdmin(next, admin);
            }

            persist.run();
            SwitchableDataSource.Active previous = dataSource.swap(next);
            swapped = true;
            retirer.schedule(() -> previous.pool().close(), RETIRE_AFTER_SECONDS, TimeUnit.SECONDS);

            String fullSummary = summary + (copied != null ? ", copied " + copied.rows() + " rows from " + copied.tables() + " tables" : "");
            log.info("DATABASE {}", fullSummary);
            try {
                audit.record("UPDATE", "Database", null, fullSummary);
            } catch (RuntimeException ex) {
                log.warn("Could not write the audit entry for the database switch", ex);
            }
            String message = copied != null
                    ? "Switched. Copied " + copied.rows() + " rows from " + copied.tables() + " tables to the new database."
                    : "Switched database." + accountNote + " Sign in again to continue.";
            return new SwitchResponse(message, copied != null ? copied.tables() : null, copied != null ? copied.rows() : null,
                    copied == null);
        } catch (FlywayException ex) {
            throw new BusinessException("Could not set up the tables in the new database: " + rootMessage(ex));
        } catch (SQLException ex) {
            throw new BusinessException("Copying data failed, nothing was changed: " + rootMessage(ex));
        } finally {
            if (!swapped) {
                next.pool().close();
            }
        }
    }

    /** Adds the signed-in admin to a database without admin accounts, so they can sign in after the switch. */
    private static String ensureAdmin(SwitchableDataSource.Active next, Admin admin) throws SQLException {
        try (Connection con = next.pool().getConnection()) {
            if (count(con, "SELECT COUNT(*) FROM admins", null) > 0) {
                boolean present = exists(con, "SELECT 1 FROM admins WHERE email = ?", admin.getEmail());
                return present ? "" : " That database has its own admin accounts: sign in with one of them.";
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO admins (id, name, email, password, role, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, admin.getId());
                ps.setString(2, admin.getName());
                ps.setString(3, admin.getEmail());
                ps.setString(4, admin.getPassword());
                ps.setString(5, admin.getRole().name());
                ps.setTimestamp(6, Timestamp.from(admin.getCreatedAt()));
                ps.executeUpdate();
            }
            try (Statement st = con.createStatement()) {
                st.execute("SELECT setval(pg_get_serial_sequence('admins', 'id'), (SELECT MAX(id) FROM admins))");
            }
            return " Your admin account (" + admin.getEmail() + ") was added to it with your current password.";
        }
    }

    // ------------------------------------------------------------------ helpers

    private Admin verifyPassword(AdminPrincipal principal, String password) {
        Admin admin = adminRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException("Your admin account was not found. Sign in again."));
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new BusinessException("currentPassword", "That password is not correct");
        }
        return admin;
    }

    private DatabaseConnection resolve(ConnectionRequest r) {
        String host = r.host().trim();
        String username = r.username().trim();
        String password = r.password();
        if (password == null || password.isEmpty()) {
            DatabaseConnection current = dataSource.active().connection();
            if (current != null && current.host().equalsIgnoreCase(host) && current.username().equals(username)) {
                password = current.password();
            } else {
                throw new BusinessException("password", "Enter the database password");
            }
        }
        String schema = r.schema() == null || r.schema().isBlank() ? null : r.schema().trim();
        return new DatabaseConnection(host, r.port(), r.database().trim(), username, password, schema, r.sslMode());
    }

    private boolean isCurrent(String jdbcUrl, String username) {
        SwitchableDataSource.Active a = dataSource.active();
        return jdbcUrl.equals(a.jdbcUrl()) && username.equals(a.username());
    }

    private Flyway flyway(DataSource ds, String schema) {
        String[] locations = flywayProperties.getLocations().stream()
                .map(l -> l.replace("{vendor}", "postgresql"))
                .toArray(String[]::new);
        FluentConfiguration cfg = Flyway.configure().dataSource(ds).locations(locations);
        if (schema != null) {
            cfg.defaultSchema(schema).schemas(schema).createSchemas(true);
        }
        return cfg.load();
    }

    static String friendlyError(Throwable ex, String host) {
        String sqlState = null;
        boolean unknownHost = false;
        boolean unreachable = false;
        Throwable root = ex;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PSQLException p && sqlState == null) {
                sqlState = p.getSQLState();
            }
            unknownHost |= t instanceof UnknownHostException;
            unreachable |= t instanceof ConnectException || t instanceof NoRouteToHostException || t instanceof SocketTimeoutException;
            root = t;
        }
        String where = host != null ? host : "the database server";
        if (unknownHost) {
            return "The host " + where + " was not found. Check the host name.";
        }
        if (unreachable) {
            String hint = host != null && SUPABASE_DIRECT_HOST.matcher(host).matches()
                    ? " Supabase direct connections (db.<project>.supabase.co) need IPv6. If this server has no IPv6, "
                      + "use the Session pooler connection string from Supabase (Connect -> Session pooler)."
                    : " Check the host and port, and that the server accepts connections from this machine.";
            return "Could not reach " + where + "." + hint;
        }
        if ("28P01".equals(sqlState) || "28000".equals(sqlState)) {
            return "The user name or password is wrong.";
        }
        if ("3D000".equals(sqlState)) {
            return "That database does not exist on the server.";
        }
        String message = rootMessage(root);
        if (message.contains("Tenant or user not found")) {
            return "The Supabase pooler did not recognise the user. For the pooler the user name is postgres.<project-ref>.";
        }
        if (message.toLowerCase().contains("ssl")) {
            return message + " Try a different SSL mode.";
        }
        return "Could not connect: " + message;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return message.length() > 400 ? message.substring(0, 400) + "..." : message;
    }

    private static String redact(String url) {
        return url == null ? null : URL_PASSWORD.matcher(url).replaceAll("$1***");
    }

    private static boolean isLocal(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]");
    }

    private static String queryString(Connection con, String sql) {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException ex) {
            return null;
        }
    }

    private static boolean exists(Connection con, String sql, String param) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static long count(Connection con, String sql, String param) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (param != null) {
                ps.setString(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static Long countOrNull(Connection con, String sql) {
        try {
            return count(con, sql, null);
        } catch (SQLException ex) {
            return null;
        }
    }
}
