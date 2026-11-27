package com.clinic.storage;

import com.clinic.entity.Admin;
import com.clinic.exception.BusinessException;
import com.clinic.repository.AdminRepository;
import com.clinic.security.AdminPrincipal;
import com.clinic.service.AuditService;
import com.clinic.storage.StorageDtos.CloudinaryRequest;
import com.clinic.storage.StorageDtos.SaveRequest;
import com.clinic.storage.StorageDtos.SaveResponse;
import com.clinic.storage.StorageDtos.StatusResponse;
import com.clinic.storage.StorageDtos.TestResponse;
import com.clinic.storage.StorageDtos.UseEnvironmentRequest;
import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.RateLimited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Admin -> Image storage: shows where new uploads go, tests Cloudinary credentials and switches the running app between
 * local disk and Cloudinary. Cloudinary settings are only saved after Cloudinary accepted them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageSettingsService {

    private final SwitchableFileStorage storage;
    private final StorageSettingsStore store;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public StatusResponse status() {
        SwitchableFileStorage.Active a = storage.active();
        CloudinarySettings c = a.cloudinary();
        String envProvider;
        CloudinarySettings envCloudinary = storage.environmentCloudinaryOrNull();
        try {
            envProvider = storage.fromEnvironment().provider();
        } catch (IllegalStateException ex) {
            envProvider = null; // backend/.env is invalid; shown as unknown
        }
        return new StatusResponse(
                a.source().name(),
                a.provider(),
                c != null ? c.cloudName() : null,
                c != null ? c.apiKey() : null,
                c != null ? c.folder() : null,
                c != null ? c.maskedUrl() : null,
                storage.local().root().toString(),
                store.file().toString(),
                a.savedAt(),
                a.savedBy(),
                envProvider,
                envCloudinary != null ? envCloudinary.cloudName() : null);
    }

    public TestResponse test(CloudinaryRequest request) {
        CloudinarySettings target = resolve(request);
        Cloudinary client = target.client();
        ping(client, target);

        String plan = null;
        Long storageBytes = null;
        Long resources = null;
        Double creditsUsed = null;
        try {
            Map<?, ?> usage = client.api().usage(Map.of());
            plan = usage.get("plan") != null ? String.valueOf(usage.get("plan")) : null;
            storageBytes = number(nested(usage, "storage", "usage"));
            resources = number(usage.get("resources"));
            creditsUsed = nested(usage, "credits", "used_percent") instanceof Number n ? n.doubleValue() : null;
        } catch (Exception ex) {
            log.debug("Cloudinary usage not available: {}", ex.getMessage()); // details are optional
        }
        CloudinarySettings current = storage.active().cloudinary();
        boolean same = current != null && Objects.equals(current.cloudName(), target.cloudName())
                && Objects.equals(current.apiKey(), target.apiKey()) && Objects.equals(current.folder(), target.folder());
        return new TestResponse(target.cloudName(), target.folder(), plan, storageBytes, resources, creditsUsed, same);
    }

    public synchronized SaveResponse save(SaveRequest request, AdminPrincipal principal) {
        verifyPassword(principal, request.currentPassword());
        SwitchableFileStorage.Active next;
        if (SwitchableFileStorage.CLOUDINARY.equals(request.provider())) {
            if (request.cloudinary() == null) {
                throw new BusinessException("cloudinary", "Enter the Cloudinary details");
            }
            CloudinarySettings target = resolve(request.cloudinary());
            CloudinaryFileStorage cloudinary = new CloudinaryFileStorage(target);
            ping(cloudinary.client(), target);
            StorageSettingsStore.Saved saved = store.save(SwitchableFileStorage.CLOUDINARY, target, principal.email());
            next = new SwitchableFileStorage.Active(SwitchableFileStorage.Source.ADMIN_PANEL, SwitchableFileStorage.CLOUDINARY,
                    cloudinary, target, saved.savedBy(), saved.savedAt());
        } else {
            StorageSettingsStore.Saved saved = store.save(SwitchableFileStorage.LOCAL, null, principal.email());
            next = new SwitchableFileStorage.Active(SwitchableFileStorage.Source.ADMIN_PANEL, SwitchableFileStorage.LOCAL,
                    storage.local(), null, saved.savedBy(), saved.savedAt());
        }
        storage.swap(next);
        String message = "New images are now stored " + SwitchableFileStorage.describe(next);
        record(message);
        return new SaveResponse(message);
    }

    public synchronized SaveResponse useEnvironment(UseEnvironmentRequest request, AdminPrincipal principal) {
        verifyPassword(principal, request.currentPassword());
        if (storage.active().source() == SwitchableFileStorage.Source.ENVIRONMENT) {
            throw new BusinessException("The app is already using the image storage from backend/.env");
        }
        SwitchableFileStorage.Active next;
        try {
            next = storage.fromEnvironment();
        } catch (IllegalStateException ex) {
            throw new BusinessException("The image storage in backend/.env cannot be used: " + ex.getMessage());
        }
        store.delete();
        storage.swap(next);
        String message = "Image storage set back to backend/.env. New images are stored " + SwitchableFileStorage.describe(next);
        record(message);
        return new SaveResponse(message);
    }

    // ------------------------------------------------------------------ helpers

    private void record(String message) {
        try {
            audit.record("UPDATE", "Image storage", null, message);
        } catch (RuntimeException ex) {
            log.warn("Could not write the audit log entry for an image storage change: {}", ex.getMessage());
        }
    }

    private void verifyPassword(AdminPrincipal principal, String password) {
        Admin admin = adminRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException("Your admin account was not found. Sign in again."));
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new BusinessException("currentPassword", "That password is not correct");
        }
    }

    /** An empty secret means "keep the saved one", allowed only for the same cloud and API key. */
    private CloudinarySettings resolve(CloudinaryRequest r) {
        CloudinarySettings requested = new CloudinarySettings(r.cloudName(), r.apiKey(), r.apiSecret(), r.folder());
        if (requested.apiSecret() != null) {
            return requested;
        }
        CloudinarySettings current = storage.active().cloudinary();
        if (current != null && current.cloudName().equals(requested.cloudName()) && current.apiKey().equals(requested.apiKey())) {
            return requested.withSecret(current.apiSecret());
        }
        throw new BusinessException("apiSecret", "Enter the API secret");
    }

    /** Throws a {@link BusinessException} with a readable reason when Cloudinary does not accept the details. */
    private static void ping(Cloudinary client, CloudinarySettings target) {
        try {
            Map<?, ?> result = client.api().ping(Map.of());
            if (result != null && result.get("status") != null && !"ok".equalsIgnoreCase(String.valueOf(result.get("status")))) {
                throw new BusinessException("Cloudinary answered '" + result.get("status") + "' instead of ok");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.info("Cloudinary test for cloud '{}' failed: {}", target.cloudName(), ex.toString());
            throw new BusinessException(friendlyError(ex, target));
        }
    }

    static String friendlyError(Exception ex, CloudinarySettings target) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException) {
                return "Could not reach Cloudinary (api.cloudinary.com). Check this server's internet connection.";
            }
            if (t instanceof SocketTimeoutException || t instanceof ConnectException
                    || t.getClass().getSimpleName().contains("Timeout")) {
                return "Cloudinary did not answer in time. Check this server's internet connection and try again.";
            }
        }
        if (ex instanceof RateLimited) {
            return "Cloudinary's request limit was reached. Wait a few minutes and try again.";
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("cloud_name") || lower.contains("cloud name")) {
            return "Cloudinary does not know the cloud name \"" + target.cloudName() + "\". Copy it from the Cloudinary console.";
        }
        if (lower.contains("api_key") || lower.contains("api key")) {
            return "Cloudinary does not accept the API key " + target.apiKey() + " for cloud \"" + target.cloudName() + "\".";
        }
        if (lower.contains("secret") || lower.contains("signature") || lower.contains("unauthorized")) {
            return "The API secret is not correct for this API key.";
        }
        String shortMessage = message.length() > 300 ? message.substring(0, 300) + "…" : message;
        return "Cloudinary did not accept these details" + (shortMessage.isBlank() ? "." : ": " + shortMessage);
    }

    private static Object nested(Map<?, ?> map, String key, String inner) {
        return map.get(key) instanceof Map<?, ?> m ? m.get(inner) : null;
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
