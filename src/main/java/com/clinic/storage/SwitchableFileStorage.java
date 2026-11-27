package com.clinic.storage;

import com.clinic.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@link FileStorage} the app uses. New uploads go to the active provider (local disk or Cloudinary), which admins
 * can change in Admin -> Image storage without a restart.
 * <p>
 * Images uploaded earlier keep working after a change: local files are always served from disk, and Cloudinary URLs
 * are absolute.
 */
@Slf4j
@Primary
@Component
public class SwitchableFileStorage implements FileStorage {

    public static final String LOCAL = "local";
    public static final String CLOUDINARY = "cloudinary";

    public enum Source { ADMIN_PANEL, ENVIRONMENT }

    /**
     * @param cloudinary null for local storage
     */
    public record Active(Source source, String provider, FileStorage storage, CloudinarySettings cloudinary,
                         String savedBy, Instant savedAt) {
    }

    private final LocalFileStorage local;
    private final StorageSettingsStore store;
    private final AppProperties properties;
    private volatile Active active;

    public SwitchableFileStorage(LocalFileStorage local, StorageSettingsStore store, AppProperties properties) {
        this.local = local;
        this.store = store;
        this.properties = properties;
        this.active = store.load()
                .map(saved -> {
                    log.info("Using the image storage saved in Admin -> Image storage ({}). Delete {} to use backend/.env instead.",
                            saved.provider(), store.file());
                    return fromSaved(saved);
                })
                .orElseGet(this::fromEnvironment);
        log.info("New image uploads are stored {}", describe(active));
    }

    public Active active() {
        return active;
    }

    public LocalFileStorage local() {
        return local;
    }

    /** Settings from STORAGE_PROVIDER / CLOUDINARY_URL in backend/.env, as used before anything was saved. */
    public Active fromEnvironment() {
        String provider = properties.storage().provider().trim().toLowerCase(Locale.ROOT);
        if (CLOUDINARY.equals(provider)) {
            return new Active(Source.ENVIRONMENT, CLOUDINARY, new CloudinaryFileStorage(environmentCloudinary()),
                    environmentCloudinary(), null, null);
        }
        if (!LOCAL.equals(provider)) {
            throw new IllegalStateException("STORAGE_PROVIDER must be 'local' or 'cloudinary', not '" + provider + "'");
        }
        return new Active(Source.ENVIRONMENT, LOCAL, local, null, null, null);
    }

    /** The Cloudinary account in CLOUDINARY_URL, or null when it is not set. */
    public CloudinarySettings environmentCloudinaryOrNull() {
        try {
            return environmentCloudinary();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private CloudinarySettings environmentCloudinary() {
        AppProperties.Storage.Cloudinary config = properties.storage().cloudinary();
        String url = config == null ? null : config.url();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "STORAGE_PROVIDER=cloudinary requires CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>");
        }
        try {
            return CloudinarySettings.fromUrl(url, config.folder());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private Active fromSaved(StorageSettingsStore.Saved saved) {
        if (CLOUDINARY.equals(saved.provider())) {
            return new Active(Source.ADMIN_PANEL, CLOUDINARY, new CloudinaryFileStorage(saved.cloudinary()),
                    saved.cloudinary(), saved.savedBy(), saved.savedAt());
        }
        return new Active(Source.ADMIN_PANEL, LOCAL, local, null, saved.savedBy(), saved.savedAt());
    }

    /** Routes new uploads to {@code next}. Returns the previous settings. */
    public synchronized Active swap(Active next) {
        Active previous = active;
        active = next;
        log.info("New image uploads are now stored {}", describe(next));
        return previous;
    }

    public static String describe(Active a) {
        return CLOUDINARY.equals(a.provider()) ? "in Cloudinary (" + a.cloudinary() + ")" : "on this server's disk";
    }

    @Override
    public StoredFile store(InputStream content, String extension, String contentType, long size) {
        return active.storage().store(content, extension, contentType, size);
    }

    /** Local files stay servable whichever provider takes new uploads. */
    @Override
    public Optional<Resource> load(String fileName) {
        return local.load(fileName);
    }
}
