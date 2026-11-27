package com.clinic.storage;

import com.cloudinary.Cloudinary;
import org.springframework.core.io.Resource;

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
 * Created by {@link SwitchableFileStorage} from Admin -> Image storage or from {@code CLOUDINARY_URL}.
 */
public class CloudinaryFileStorage implements FileStorage {

    private final Cloudinary cloudinary;
    private final CloudinarySettings settings;

    public CloudinaryFileStorage(CloudinarySettings settings) {
        if (settings == null || settings.cloudName() == null || settings.apiKey() == null || settings.apiSecret() == null) {
            throw new IllegalStateException(
                    "Cloudinary storage needs a cloud name, API key and API secret "
                            + "(CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>)");
        }
        this.settings = settings;
        this.cloudinary = settings.client();
    }

    public CloudinarySettings settings() {
        return settings;
    }

    /** Raw client, used to test the connection. */
    Cloudinary client() {
        return cloudinary;
    }

    @Override
    public StoredFile store(InputStream content, String extension, String contentType, long size) {
        String publicId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> options = new HashMap<>();
        options.put("public_id", publicId);
        options.put("resource_type", "image");
        options.put("overwrite", false);
        if (settings.folder() != null) {
            options.put("folder", settings.folder());
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
