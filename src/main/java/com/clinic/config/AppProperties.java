package com.clinic.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Strongly typed application configuration bound from the {@code app.*} properties (environment variables). */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String timezone,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cookie cookie,
        @Valid @NotNull AdminBootstrap adminBootstrap,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Storage storage
) {
    /**
     * @param configFile where the storage chosen in Admin -> Image storage is saved. When this file exists it wins over
     *                   {@code provider} and {@code cloudinary}; delete it to go back to them.
     */
    public record Storage(@NotBlank String provider, @NotBlank String uploadDir, @Min(1024) long maxFileSizeBytes,
                          Cloudinary cloudinary, String configFile) {

        /** Only used when {@code provider=cloudinary}; {@code url} is the CLOUDINARY_URL connection string. */
        public record Cloudinary(String url, String folder) {
        }
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    public record Jwt(
            @NotBlank(message = "JWT_SECRET environment variable must be set")
            @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
            String secret,
            @Min(1) long accessTokenTtlMinutes,
            @Min(1) long refreshTokenTtlDays,
            @NotBlank String issuer) {
    }

    public record Cookie(boolean secure, @NotBlank String sameSite) {
    }

    public record AdminBootstrap(String name, String email, String password) {
    }

    public record RateLimit(@Min(1) int bookingPerHour, @Min(1) int loginPerMinute, @Min(1) int refreshPerMinute) {
    }
}
