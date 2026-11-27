package com.clinic.database;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Database connection settings that are not part of {@code spring.datasource}.
 *
 * @param configFile where the connection chosen in Admin -> Database is saved. When this file exists it wins over the
 *                   DB_* environment variables; delete it to go back to them.
 * @param poolSize   maximum number of pooled connections.
 * @param schema     schema of the DB_* (.env) database that holds the clinic tables (DB_SCHEMA). Blank or "public"
 *                   means the server default.
 */
@ConfigurationProperties(prefix = "app.database")
public record DatabaseProperties(
        @DefaultValue("./db-connection.json") String configFile,
        @DefaultValue("5") int poolSize,
        @DefaultValue("") String schema
) {
}
