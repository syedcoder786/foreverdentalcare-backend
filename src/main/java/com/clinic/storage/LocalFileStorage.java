package com.clinic.storage;

import com.clinic.config.AppProperties;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploads on the local filesystem and serves them via GET /api/public/files/{fileName}. Always present, so
 * files uploaded before a switch to Cloudinary keep being served. The app uses it through {@link SwitchableFileStorage}.
 */
@Component
public class LocalFileStorage implements FileStorage {

    /** Generated names only: protects against path traversal on load. */
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9]{32}\\.(jpg|png|webp|gif)$");
    public static final String PUBLIC_PREFIX = "/api/public/files/";

    private final Path root;

    public LocalFileStorage(AppProperties properties) {
        this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    @Override
    public StoredFile store(InputStream content, String extension, String contentType, long size) {
        String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(name);
        try {
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store uploaded file", e);
        }
        return new StoredFile(PUBLIC_PREFIX + name, name, contentType, size);
    }

    @Override
    public Optional<Resource> load(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(new PathResource(file));
    }
}
