package com.clinic.database;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * The application's only {@link javax.sql.DataSource}. It forwards every request to the active connection pool,
 * which an admin can replace at runtime (Admin -> Database) without restarting the app. JPA, Flyway and the
 * repositories all go through this object, so after a switch new transactions use the new database.
 */
public class SwitchableDataSource extends AbstractDataSource implements DisposableBean {

    public enum Source {
        /** DB_* environment variables (backend/.env). */
        ENVIRONMENT,
        /** The connection saved from Admin -> Database. */
        ADMIN_PANEL
    }

    /**
     * @param connection the saved connection; null when {@code source} is ENVIRONMENT.
     * @param envSchema  clinic schema of the ENVIRONMENT connection (DB_SCHEMA); ignored for ADMIN_PANEL.
     */
    public record Active(HikariDataSource pool, Source source, DatabaseConnection connection, String jdbcUrl, String username,
                         String envSchema) {

        /** Clinic schema of this connection, or null for the server default ({@code public}). */
        public String schema() {
            if (connection != null) {
                return connection.hasSchema() ? connection.schema() : null;
            }
            return envSchema != null && !envSchema.isBlank() && !"public".equals(envSchema) ? envSchema : null;
        }
    }

    private volatile Active active;

    public SwitchableDataSource(Active initial) {
        this.active = initial;
    }

    public Active active() {
        return active;
    }

    /** Makes {@code next} the active pool and returns the previous one, which the caller must close. */
    public synchronized Active swap(Active next) {
        Active previous = active;
        active = next;
        return previous;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return active.pool().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return active.pool().getConnection(username, password);
    }

    @Override
    public void destroy() {
        active.pool().close();
    }
}
