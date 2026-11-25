package com.clinic.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Image storage abstraction. {@link LocalFileStorage} keeps files on disk for local/single-server deployments.
 * To use Cloudinary, S3 or another object store, implement this interface, return absolute public URLs from
 * {@link #store}, and activate it with {@code app.storage.provider}.
 */
public interface FileStorage {

    /** Stores the bytes under a generated, collision-free name and returns the public URL. */
    StoredFile store(InputStream content, String extension, String contentType, long size);

    /** Loads a stored file for serving (only meaningful for providers that serve through this API). */
    Optional<Resource> load(String fileName);

    record StoredFile(String url, String fileName, String contentType, long size) {
    }
}
