package com.clinic.storage;

import com.clinic.config.AppProperties;
import com.cloudinary.Cloudinary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores uploads in Cloudinary and returns their absolute {@code https://res.cloudinary.com/...} URL.
 * Images are served directly by Cloudinary's CDN, so {@link #load} never returns anything.
 * Activate with {@code STORAGE_PROVIDER=cloudinary} and {@code CLOUDINARY_URL=cloudinary://key:secret@cloud}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
public class CloudinaryFileStorage implements FileStorage {

    private final Cloudinary cloudinary;
    private final String folder;

    public CloudinaryFileStorage(AppProperties properties) {
        AppProperties.Storage.Cloudinary config = properties.storage().cloudinary();
        String url = config == null ? null : config.url();
        if (config == null || url == null || url.isBlank() || !url.trim().startsWith("cloudinary://")) {
            throw new IllegalStateException(
                    "STORAGE_PROVIDER=cloudinary requires CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>");
        }
        this.cloudinary = new Cloudinary(url.trim());
        this.cloudinary.config.secure = true;
        String configuredFolder = config.folder();
        this.folder = configuredFolder == null || configuredFolder.isBlank() ? null : configuredFolder.trim();
        log.info("Image uploads are stored in Cloudinary (cloud '{}', folder '{}')",
                cloudinary.config.cloudName, folder == null ? "" : folder);
    }

    @Override
    public StoredFile store(InputStream content, String extension, String contentType, long size) {
        String publicId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> options = new HashMap<>();
        options.put("public_id", publicId);
        options.put("resource_type", "image");
        options.put("overwrite", false);
        if (folder != null) {
            options.put("folder", folder);
        }
        try {
            byte[] bytes = content.readAllBytes();
            Map<?, ?> result = cloudinary.uploader().upload(bytes, options);
            String url = String.valueOf(result.get("secure_url"));
            // Same short "<32 hex>.<ext>" name as local storage (the folder is already part of the URL).
            return new StoredFile(url, publicId + "." + extension, contentType, size);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not upload image to Cloudinary", e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not upload image to Cloudinary", e);
        }
    }

    @Override
    public Optional<Resource> load(String fileName) {
        // Cloudinary serves the images itself; nothing is served through /api/public/files.
        return Optional.empty();
    }
}
