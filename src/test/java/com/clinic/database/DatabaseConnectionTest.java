package com.clinic.database;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConnectionTest {

    @Test
    void buildsSupabaseUrlWithClinicSchema() {
        DatabaseConnection c = new DatabaseConnection("db.abc.supabase.co", 5432, "postgres", "postgres", "p@ss",
                "dental_clinic", "require");
        assertThat(c.jdbcUrl()).isEqualTo("jdbc:postgresql://db.abc.supabase.co:5432/postgres?sslmode=require"
                + "&currentSchema=dental_clinic%2Cextensions%2Cpublic&connectTimeout=15&ApplicationName=dental-clinic");
        assertThat(c.toString()).doesNotContain("p@ss");
    }

    @Test
    void transactionPoolerDisablesPreparedStatementsAndPublicSchemaAddsNoSearchPath() {
        DatabaseConnection c = new DatabaseConnection("aws-0-ap-south-1.pooler.supabase.com", 6543, "postgres",
                "postgres.abc", "x", "public", "require");
        assertThat(c.jdbcUrl()).contains("prepareThreshold=0").doesNotContain("currentSchema");
    }

    @Test
    void ipv6HostIsBracketed() {
        DatabaseConnection c = new DatabaseConnection("2406:da14::1", 5432, "postgres", "postgres", "x", null, "require");
        assertThat(c.jdbcUrl()).startsWith("jdbc:postgresql://[2406:da14::1]:5432/postgres?");
    }

    @Test
    void insertOrderPutsParentsFirst() {
        List<String> order = DatabaseCopier.insertOrder(
                List.of("appointments", "doctors", "patients", "services", "navigation_items"),
                Map.of("appointments", Set.of("doctors", "patients", "services"),
                        "navigation_items", Set.of("navigation_items")));
        assertThat(order).hasSize(5);
        assertThat(order.indexOf("appointments"))
                .isGreaterThan(order.indexOf("doctors"))
                .isGreaterThan(order.indexOf("patients"))
                .isGreaterThan(order.indexOf("services"));
    }
}
