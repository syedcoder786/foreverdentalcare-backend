package com.clinic.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Optional;

/**
 * Saves the connection chosen in Admin -> Database to a small JSON file on the server. It cannot live in the database
 * itself: the app has to know where the database is before it can read anything from it.
 * <p>
 * The file holds the database password, like backend/.env. Keep it out of git and backups you share.
 */
public class DatabaseConnectionStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** On-disk format. {@code savedBy} and {@code savedAt} are informational only. */
    record Saved(int version, DatabaseConnection connection, String savedBy, Instant savedAt) {
    }

    private final Path file;

    public DatabaseConnectionStore(String file) {
        this.file = Path.of(file).toAbsolutePath().normalize();
    }

    public Path file() {
        return file;
    }

    public Optional<Saved> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.toFile(), Saved.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the saved database connection in " + file
                    + ". Fix or delete that file to use the DB_* settings from backend/.env.", ex);
        }
    }

    public void save(DatabaseConnection connection, String savedBy) {
        try {
            Path dir = file.getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".db-connection", ".tmp");
            try {
                restrictToOwner(tmp);
                MAPPER.writeValue(tmp.toFile(), new Saved(1, connection, savedBy, Instant.now()));
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save the database connection to " + file, ex);
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not delete " + file, ex);
        }
    }

    private static void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: no POSIX permissions; the file inherits the folder's permissions.
        }
    }
}
