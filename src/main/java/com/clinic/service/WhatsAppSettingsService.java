package com.clinic.service;

import com.clinic.dto.WhatsAppDtos.WhatsAppSettingsRequest;
import com.clinic.dto.WhatsAppDtos.WhatsAppSettingsResponse;
import com.clinic.entity.WhatsAppSettings;
import com.clinic.exception.BusinessException;
import com.clinic.repository.WhatsAppSettingsRepository;
import com.clinic.util.PhoneNumbers;
import com.clinic.util.ValidationPatterns;
import com.clinic.whatsapp.WhatsAppClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Admin management of the WhatsApp Cloud API settings used for patient sign-in codes. */
@Service
@RequiredArgsConstructor
public class WhatsAppSettingsService {

    private final WhatsAppSettingsRepository repository;
    private final WhatsAppClient whatsAppClient;
    private final AuditService audit;

    /** The settings row (created with defaults if missing). */
    @Transactional
    public WhatsAppSettings load() {
        return repository.findById(WhatsAppSettings.SINGLETON_ID).orElseGet(() -> repository.save(new WhatsAppSettings()));
    }

    @Transactional
    public WhatsAppSettingsResponse get() {
        return toResponse(load());
    }

    @Transactional
    public WhatsAppSettingsResponse update(WhatsAppSettingsRequest r) {
        WhatsAppSettings s = load();
        s.setPhoneNumberId(ValidationPatterns.trimToNull(r.phoneNumberId()));
        s.setBusinessAccountId(ValidationPatterns.trimToNull(r.businessAccountId()));
        String newToken = ValidationPatterns.trimToNull(r.accessToken());
        if (newToken != null) {
            s.setAccessToken(newToken);
        }
        s.setOtpTemplateName(ValidationPatterns.trimToNull(r.otpTemplateName()));
        s.setOtpTemplateLanguage(r.otpTemplateLanguage().trim());
        s.setDefaultCountryCode(r.defaultCountryCode().trim());
        if (r.enabled() && !s.isConfigured()) {
            String missing = s.getPhoneNumberId() == null ? "phoneNumberId"
                    : s.getAccessToken() == null ? "accessToken" : "otpTemplateName";
            throw new BusinessException(missing, "Fill in the Phone number ID, access token and template name before turning WhatsApp sign-in on");
        }
        s.setEnabled(r.enabled());
        WhatsAppSettings saved = repository.save(s);
        audit.record("UPDATE", "WhatsAppSettings", saved.getId(),
                (saved.isEnabled() ? "Enabled" : "Disabled") + (newToken != null ? ", access token replaced" : ""));
        return toResponse(saved);
    }

    /**
     * Sends a sample code (123456) with the saved settings, so admins can check the setup. Any approved template is
     * accepted here (e.g. hello_world to test the connection); the returned message warns when the template can't
     * carry a code and therefore won't work for patient sign-in.
     */
    @Transactional
    public String sendTest(String rawPhone) {
        WhatsAppSettings s = load();
        if (!s.isConfigured()) {
            throw new BusinessException("Save the Phone number ID, access token and template name first");
        }
        String phone = PhoneNumbers.toE164(rawPhone, s.getDefaultCountryCode());
        Optional<WhatsAppClient.TemplateInfo> shape;
        try {
            shape = whatsAppClient.sendOtp(s, phone, "123456", false);
        } catch (WhatsAppClient.WhatsAppException ex) {
            String hint = ex.getMessage().contains("132001")
                    ? " Create an approved template of category Authentication (with a Copy code button) named '"
                      + s.getOtpTemplateName() + "' in language " + s.getOtpTemplateLanguage()
                      + " in WhatsApp Manager, or enter the exact name and language of an existing one."
                    : "";
            throw new BusinessException("phone", "WhatsApp error: " + ex.getMessage() + hint);
        }
        audit.record("TEST", "WhatsAppSettings", s.getId(), "Test message sent to " + PhoneNumbers.mask(phone));
        if (shape.isPresent() && shape.get().bodyParams() == 0) {
            return "Connection works: '" + shape.get().name() + "' was sent to " + PhoneNumbers.mask(phone)
                    + ". But this template has no {{1}} placeholder, so it can't carry sign-in codes. "
                    + "Create an Authentication template with a Copy code button and enter its name here.";
        }
        return "Test code 123456 sent to " + PhoneNumbers.mask(phone) + ". Check WhatsApp on that number.";
    }

    private static WhatsAppSettingsResponse toResponse(WhatsAppSettings s) {
        String token = s.getAccessToken();
        boolean tokenSet = token != null && !token.isBlank();
        return new WhatsAppSettingsResponse(
                s.isEnabled(),
                s.getPhoneNumberId(),
                s.getBusinessAccountId(),
                tokenSet,
                tokenSet && token.length() >= 8 ? token.substring(token.length() - 4) : null,
                s.getOtpTemplateName(),
                s.getOtpTemplateLanguage(),
                s.getDefaultCountryCode(),
                s.isReady(),
                s.getUpdatedAt());
    }
}
