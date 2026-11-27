package com.clinic.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Admin -> Image storage API. The API secret is write-only: responses never contain it. */
public final class StorageDtos {

    private StorageDtos() {
    }

    public record CloudinaryRequest(
            @NotBlank(message = "Enter the cloud name")
            @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,100}$", message = "Cloud names use letters, digits, _ and -")
            String cloudName,

            @NotBlank(message = "Enter the API key")
            @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,100}$", message = "The API key uses letters and digits only")
            String apiKey,

            /** Leave empty to keep the saved secret of the same cloud and API key. */
            @Size(max = 200)
            @Pattern(regexp = "^$|^[A-Za-z0-9_\\-]+$", message = "The API secret uses letters, digits, _ and -")
            String apiSecret,

            @Size(max = 200)
            @Pattern(regexp = "^$|^/?[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)*/?$",
                    message = "Folder names use letters, digits, _ and -, separated by /")
            String folder
    ) {
    }

    public record SaveRequest(
            @NotBlank @Pattern(regexp = "^(local|cloudinary)$", message = "Choose local or cloudinary") String provider,
            /** Required when provider is cloudinary. */
            @Valid CloudinaryRequest cloudinary,
            @NotBlank(message = "Enter your admin password to confirm") String currentPassword
    ) {
    }

    /** Go back to STORAGE_PROVIDER / CLOUDINARY_URL in backend/.env. */
    public record UseEnvironmentRequest(
            @NotBlank(message = "Enter your admin password to confirm") String currentPassword
    ) {
    }

    public record StatusResponse(
            /** ADMIN_PANEL or ENVIRONMENT. */
            String source,
            /** local or cloudinary: where new uploads go. */
            String provider,
            String cloudName,
            String apiKey,
            String folder,
            /** CLOUDINARY_URL with the secret hidden. */
            String cloudinaryUrl,
            /** Folder on the server used by local storage (old local images are always served from here). */
            String uploadDir,
            /** Where the admin-panel settings are saved. */
            String configFile,
            Instant savedAt,
            String savedBy,
            /** What backend/.env says, used by "Go back to .env settings". */
            String environmentProvider,
            String environmentCloudName
    ) {
    }

    public record TestResponse(
            String cloudName,
            String folder,
            /** Cloudinary plan, e.g. "Free". */
            String plan,
            Long storageBytes,
            Long resources,
            /** Monthly credits used, in percent. */
            Double creditsUsedPercent,
            boolean sameAsCurrent
    ) {
    }

    public record SaveResponse(String message) {
    }
}
