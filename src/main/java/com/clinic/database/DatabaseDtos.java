package com.clinic.database;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Admin -> Database API. Passwords are write-only: responses never contain them. */
public final class DatabaseDtos {

    private DatabaseDtos() {
    }

    public record ConnectionRequest(
            @NotBlank @Size(max = 255)
            @Pattern(regexp = "^[A-Za-z0-9.\\-]+$|^\\[?[0-9A-Fa-f:]+]?$", message = "Enter a host name such as db.xxxx.supabase.co")
            String host,

            @NotNull @Min(1) @Max(65535)
            Integer port,

            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,63}$", message = "Database names may use letters, digits, _ and -")
            String database,

            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.\\-]{1,100}$", message = "User names may use letters, digits, _, . and -")
            String username,

            /** Leave empty to keep the saved password of the same host and user. */
            @Size(max = 500)
            String password,

            @Pattern(regexp = "^$|^[a-z_][a-z0-9_]{0,62}$", message = "Schema names use lower-case letters, digits and _, and start with a letter")
            String schema,

            @NotBlank @Pattern(regexp = "^(disable|allow|prefer|require|verify-ca|verify-full)$", message = "Choose an SSL mode")
            String sslMode
    ) {
    }

    public record SwitchRequest(
            @Valid @NotNull ConnectionRequest connection,
            /** Replace everything in the new database with a copy of the current one. */
            boolean copyData,
            @NotBlank(message = "Enter your admin password to confirm") String currentPassword
    ) {
    }

    /** Go back to the DB_* settings in backend/.env. */
    public record UseEnvironmentRequest(
            boolean copyData,
            @NotBlank(message = "Enter your admin password to confirm") String currentPassword
    ) {
    }

    public record StatusResponse(
            /** ADMIN_PANEL or ENVIRONMENT. */
            String source,
            String host,
            Integer port,
            String database,
            String username,
            String schema,
            String sslMode,
            /** JDBC URL of the active connection (never contains the password). */
            String jdbcUrl,
            boolean connected,
            String error,
            String serverVersion,
            String currentSchema,
            /** Latest applied migration. */
            String schemaVersion,
            Integer activeConnections,
            Integer totalConnections,
            Integer maxConnections,
            /** Where the admin-panel connection is saved. */
            String configFile,
            Instant savedAt,
            String savedBy,
            /** JDBC URL from backend/.env, used by "Go back to .env database". */
            String environmentJdbcUrl
    ) {
    }

    public record TestResponse(
            String serverVersion,
            String database,
            /** True when the clinic schema is missing and will be created. */
            boolean schemaWillBeCreated,
            /** Latest applied migration in the target, null for an empty database. */
            String schemaVersion,
            int pendingMigrations,
            /** Admin accounts already in the target. */
            Long admins,
            /** Clinic records already in the target (patients, appointments, doctors). */
            Long patients,
            Long appointments,
            Long doctors,
            /** Problems that will stop a switch, and things worth knowing. */
            List<String> errors,
            List<String> warnings,
            boolean sameAsCurrent
    ) {
    }

    public record SwitchResponse(
            String message,
            Integer tablesCopied,
            Long rowsCopied,
            /** The admin must sign in again (their session lives in the old database). */
            boolean signInAgain
    ) {
    }
}
