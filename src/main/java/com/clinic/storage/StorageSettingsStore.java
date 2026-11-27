package com.clinic.storage;

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
 * Saves the image storage chosen in Admin -> Image storage to a small JSON file on the server
 * (default {@code storage-connection.json}). When the file exists it wins over STORAGE_PROVIDER / CLOUDINARY_URL.
 * <p>
 * The file holds the Cloudinary API secret, like backend/.env. Keep it out of git and backups you share.
 */
public class StorageSettingsStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * On-disk format. {@code cloudinary} is null for local storage. {@code savedBy} and {@code savedAt} are informational.
     */
    public record Saved(int version, String provider, CloudinarySettings cloudinary, String savedBy, Instant savedAt) {
    }

    private final Path file;

    public StorageSettingsStore(String file) {
        this.file = Path.of(file == null || file.isBlank() ? "./storage-connection.json" : file).toAbsolutePath().normalize();
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
            throw new IllegalStateException("Could not read the saved image storage settings in " + file
                    + ". Fix or delete that file to use STORAGE_PROVIDER / CLOUDINARY_URL from backend/.env.", ex);
        }
    }

    public Saved save(String provider, CloudinarySettings cloudinary, String savedBy) {
        Saved saved = new Saved(1, provider, cloudinary, savedBy, Instant.now());
        try {
            Path dir = file.getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".storage-connection", ".tmp");
            try {
                restrictToOwner(tmp);
                MAPPER.writeValue(tmp.toFile(), saved);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save the image storage settings to " + file, ex);
        }
        return saved;
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
