package com.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Admin WhatsApp Cloud API settings. The access token is write-only: responses never contain it. */
public final class WhatsAppDtos {

    private WhatsAppDtos() {
    }

    public record WhatsAppSettingsResponse(
            boolean enabled,
            String phoneNumberId,
            String businessAccountId,
            boolean accessTokenSet,
            /** Last 4 characters of the saved token, so admins can tell which one is stored. */
            String accessTokenLast4,
            String otpTemplateName,
            String otpTemplateLanguage,
            String defaultCountryCode,
            /** Enabled and every required value present: sign-in codes will be sent. */
            boolean ready,
            Instant updatedAt
    ) {
    }

    public record WhatsAppSettingsRequest(
            boolean enabled,

            @Size(max = 50) @Pattern(regexp = "^$|^\\d{5,50}$", message = "Phone number ID is the numeric ID shown in the WhatsApp API setup page")
            String phoneNumberId,

            @Size(max = 50) @Pattern(regexp = "^$|^\\d{5,50}$", message = "WhatsApp Business Account ID must be numeric")
            String businessAccountId,

            /** New token. Leave empty to keep the saved one. */
            @Size(max = 1000) @Pattern(regexp = "^\\S*$", message = "The access token must not contain spaces")
            String accessToken,

            @Size(max = 100) @Pattern(regexp = "^$|^[a-z0-9_]+$", message = "Template names use lowercase letters, numbers and underscores")
            String otpTemplateName,

            @NotBlank(message = "Template language is required")
            @Pattern(regexp = "^[a-z]{2,3}(_[A-Z]{2})?$", message = "Use a language code like en_US, en or hi")
            String otpTemplateLanguage,

            @NotBlank(message = "Country code is required")
            @Pattern(regexp = "^\\d{1,3}$", message = "Country code is 1-3 digits, without + (e.g. 91)")
            String defaultCountryCode
    ) {
    }

    public record WhatsAppTestRequest(
            @NotBlank(message = "Enter a WhatsApp number to send the test code to")
            String phone
    ) {
    }
}
