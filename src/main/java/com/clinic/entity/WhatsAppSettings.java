package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** WhatsApp Cloud API configuration used to send sign-in codes (single row, id = 1). */
@Entity
@Table(name = "whatsapp_settings")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "phone_number_id", length = 50)
    private String phoneNumberId;

    @Column(name = "business_account_id", length = 50)
    private String businessAccountId;

    /** Secret. Never returned by any API; only its last 4 characters are shown to admins. */
    @Column(name = "access_token", length = 1000)
    private String accessToken;

    @Column(name = "otp_template_name", length = 100)
    private String otpTemplateName;

    @Column(name = "otp_template_language", nullable = false, length = 20)
    private String otpTemplateLanguage = "en_US";

    /** Country calling code (digits only) added to numbers entered without one, e.g. "91". */
    @Column(name = "default_country_code", nullable = false, length = 4)
    private String defaultCountryCode = "91";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** True when every value needed to send a template message is present. */
    public boolean isConfigured() {
        return notBlank(phoneNumberId) && notBlank(accessToken) && notBlank(otpTemplateName) && notBlank(otpTemplateLanguage);
    }

    public boolean isReady() {
        return enabled && isConfigured();
    }

    /** WhatsApp is switched on and can send template messages (appointment updates, marketing). */
    public boolean canSendMessages() {
        return enabled && notBlank(phoneNumberId) && notBlank(accessToken);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
