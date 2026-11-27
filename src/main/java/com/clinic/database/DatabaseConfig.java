package com.clinic.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Chooses the database at startup: the connection saved from Admin -> Database if there is one, otherwise the DB_*
 * environment variables. Spring Boot's own DataSource auto-configuration backs off because this one exists.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    @Bean
    DatabaseConnectionStore databaseConnectionStore(DatabaseProperties properties) {
        return new DatabaseConnectionStore(properties.configFile());
    }

    @Bean
    @Primary
    SwitchableDataSource dataSource(DataSourceProperties envProperties, DatabaseProperties properties,
                                    DatabaseConnectionStore store) {
        return store.load()
                .map(saved -> {
                    log.info("Using the database saved in Admin -> Database: {} (from {}). Delete that file to use backend/.env instead.",
                            saved.connection().describe(), store.file());
                    return new SwitchableDataSource(adminPanelPool(saved.connection(), properties.poolSize(), false));
                })
                .orElseGet(() -> new SwitchableDataSource(environmentPool(envProperties, properties.poolSize())));
    }

    /** Points Flyway at the clinic schema of a saved connection (created on first start). */
    @Bean
    FlywayConfigurationCustomizer clinicSchemaFlywayCustomizer(SwitchableDataSource dataSource) {
        return configuration -> {
            String schema = dataSource.active().schema();
            if (schema != null) {
                configuration.defaultSchema(schema).schemas(schema).createSchemas(true);
            }
        };
    }

    static SwitchableDataSource.Active environmentPool(DataSourceProperties env, int poolSize) {
        HikariDataSource pool = env.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        pool.setMaximumPoolSize(poolSize);
        pool.setPoolName("db-env");
        return new SwitchableDataSource.Active(pool, SwitchableDataSource.Source.ENVIRONMENT, null,
                env.determineUrl(), env.determineUsername());
    }

    /**
     * @param connectNow open a connection immediately, so a wrong host or password fails here with a clear error.
     */
    static SwitchableDataSource.Active adminPanelPool(DatabaseConnection c, int poolSize, boolean connectNow) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(c.jdbcUrl());
        cfg.setUsername(c.username());
        cfg.setPassword(c.password());
        cfg.setDriverClassName("org.postgresql.Driver");
        cfg.setMaximumPoolSize(poolSize);
        cfg.setPoolName("db-" + Long.toString(System.currentTimeMillis(), 36));
        cfg.setConnectionTimeout(20_000);
        cfg.setInitializationFailTimeout(connectNow ? 1 : -1);
        return new SwitchableDataSource.Active(new HikariDataSource(cfg), SwitchableDataSource.Source.ADMIN_PANEL, c,
                c.jdbcUrl(), c.username());
    }
}
