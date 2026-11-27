package com.clinic.service;

import com.clinic.dto.WhatsAppTemplateDtos.MetaButton;
import com.clinic.dto.WhatsAppTemplateDtos.MetaTemplateResponse;
import com.clinic.dto.WhatsAppTemplateDtos.TemplateNameRequest;
import com.clinic.dto.WhatsAppTemplateDtos.TemplateNameResponse;
import com.clinic.dto.WhatsAppTemplateDtos.WhatsAppTemplateRequest;
import com.clinic.dto.WhatsAppTemplateDtos.WhatsAppTemplateResponse;
import com.clinic.entity.JsonValues.TemplateParam;
import com.clinic.entity.WhatsAppSettings;
import com.clinic.entity.WhatsAppTemplate;
import com.clinic.entity.WhatsAppTemplateName;
import com.clinic.enums.TemplateParamSource;
import com.clinic.enums.TemplatePurpose;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.repository.WhatsAppTemplateNameRepository;
import com.clinic.repository.WhatsAppTemplateRepository;
import com.clinic.util.PhoneNumbers;
import com.clinic.util.ValidationPatterns;
import com.clinic.whatsapp.TemplateValueResolver;
import com.clinic.whatsapp.TemplateValueResolver.MessageContext;
import com.clinic.whatsapp.WhatsAppClient;
import com.clinic.whatsapp.WhatsAppClient.TemplateInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Admin management of WhatsApp message templates: which approved Meta template to use and what fills it. */
@Service
@RequiredArgsConstructor
public class WhatsAppTemplateService {

    private static final Map<TemplatePurpose, String> LABELS = Map.of(
            TemplatePurpose.APPOINTMENT_RECEIVED, "Appointment received",
            TemplatePurpose.APPOINTMENT_CONFIRMED, "Appointment confirmed",
            TemplatePurpose.APPOINTMENT_CANCELLED, "Appointment cancelled",
            TemplatePurpose.APPOINTMENT_RESCHEDULED, "Appointment rescheduled");

    private final WhatsAppTemplateRepository repository;
    private final WhatsAppTemplateNameRepository nameRepository;
    private final WhatsAppSettingsService settingsService;
    private final WhatsAppClient client;
    private final TemplateValueResolver resolver;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<WhatsAppTemplateResponse> list() {
        return repository.findAllByOrderByIdAsc().stream()
                .sorted(Comparator.comparing((WhatsAppTemplate t) -> !t.getPurpose().isAutomatic())
                        .thenComparing(t -> t.getPurpose().ordinal())
                        .thenComparing(WhatsAppTemplate::getId))
                .map(WhatsAppTemplateService::toResponse)
                .toList();
    }

    // ---------- saved template names ----------

    @Transactional(readOnly = true)
    public List<TemplateNameResponse> names() {
        return nameRepository.findAllByOrderByNameAscLanguageAsc().stream()
                .map(n -> new TemplateNameResponse(n.getId(), n.getName(), n.getLanguage(),
                        repository.existsByTemplateNameAndTemplateLanguage(n.getName(), n.getLanguage())))
                .toList();
    }

    @Transactional
    public TemplateNameResponse addName(TemplateNameRequest r) {
        String name = r.name().trim();
        String lang = ValidationPatterns.trimToNull(r.language());
        String language = lang == null ? "en_US" : lang;
        if (nameRepository.existsByNameAndLanguage(name, language)) {
            throw new BusinessException("name", "'" + name + "' (" + language + ") is already saved");
        }
        WhatsAppTemplateName n = new WhatsAppTemplateName();
        n.setName(name);
        n.setLanguage(language);
        WhatsAppTemplateName saved = nameRepository.save(n);
        audit.record("CREATE", "WhatsAppTemplateName", saved.getId(), name + " (" + language + ")");
        return new TemplateNameResponse(saved.getId(), name, language, false);
    }

    @Transactional
    public void deleteName(Long id) {
        WhatsAppTemplateName n = loadName(id);
        if (repository.existsByTemplateNameAndTemplateLanguage(n.getName(), n.getLanguage())) {
            throw new BusinessException("'" + n.getName() + "' is used by a message. Change or remove that message first.");
        }
        nameRepository.delete(n);
        audit.record("DELETE", "WhatsAppTemplateName", id, n.getName() + " (" + n.getLanguage() + ")");
    }

    /** Reads the saved template's text and placeholders from Meta, for the preview and value mapping. */
    @Transactional
    public MetaTemplateResponse nameDetails(Long id) {
        WhatsAppTemplateName n = loadName(id);
        try {
            return toMeta(client.describe(requireConnection(), n.getName(), n.getLanguage()));
        } catch (WhatsAppClient.WhatsAppException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    // ---------- messages ----------

    @Transactional
    public WhatsAppTemplateResponse create(WhatsAppTemplateRequest r) {
        TemplatePurpose purpose = r.purpose() == null ? TemplatePurpose.MARKETING : r.purpose();
        if (purpose.isAutomatic() && repository.existsByPurpose(purpose)) {
            throw new BusinessException("purpose", "A message for this event already exists. Edit that one instead.");
        }
        WhatsAppTemplate t = new WhatsAppTemplate();
        t.setPurpose(purpose);
        if (purpose.isAutomatic()) {
            t.setLabel(LABELS.get(purpose));
        }
        apply(t, r);
        WhatsAppTemplate saved = repository.save(t);
        audit.record("CREATE", "WhatsAppTemplate", saved.getId(), saved.getLabel());
        return toResponse(saved);
    }

    @Transactional
    public WhatsAppTemplateResponse update(Long id, WhatsAppTemplateRequest r) {
        WhatsAppTemplate t = load(id);
        apply(t, r);
        audit.record("UPDATE", "WhatsAppTemplate", id, t.getLabel() + (t.isEnabled() ? " (on): " + t.getTemplateName() : " (off)"));
        return toResponse(t);
    }

    @Transactional
    public void delete(Long id) {
        WhatsAppTemplate t = load(id);
        repository.delete(t);
        audit.record("DELETE", "WhatsAppTemplate", id, t.getLabel());
    }

    /** Sends the template with example data to {@code rawPhone}. */
    @Transactional
    public String sendTest(Long id, String rawPhone) {
        WhatsAppTemplate t = load(id);
        WhatsAppSettings settings = requireConnection();
        if (t.getTemplateName() == null) {
            throw new BusinessException("templateName", "Choose a template name and save it first");
        }
        TemplateInfo info = checkAgainstMeta(t, settings);
        String phone = PhoneNumbers.toE164(rawPhone, settings.getDefaultCountryCode());
        try {
            client.sendTemplate(settings, phone, info, resolver.resolve(t, MessageContext.sample()));
        } catch (WhatsAppClient.WhatsAppException ex) {
            throw new BusinessException("phone", "WhatsApp error: " + ex.getMessage());
        }
        audit.record("TEST", "WhatsAppTemplate", id, t.getLabel() + " sent to " + PhoneNumbers.mask(phone));
        return "Test message sent to " + PhoneNumbers.mask(phone) + " with example data.";
    }

    /** Reads the template from Meta and checks it is approved and every placeholder has a value. */
    TemplateInfo checkAgainstMeta(WhatsAppTemplate t, WhatsAppSettings settings) {
        TemplateInfo info;
        try {
            info = client.describe(settings, t.getTemplateName(), t.getTemplateLanguage());
        } catch (WhatsAppClient.WhatsAppException ex) {
            throw new BusinessException("templateName", ex.getMessage());
        }
        if (!info.approved()) {
            throw new BusinessException("templateName", "Template '" + info.name() + "' is " + info.status() + ". It must be APPROVED in WhatsApp Manager.");
        }
        requireCount("headerParams", "header", info.headerParams(), t.getHeaderParams());
        requireCount("bodyParams", "body", info.bodyParams(), t.getBodyParams());
        requireCount("buttonParams", "buttons", info.valueButtons().size(), t.getButtonParams());
        if (info.mediaHeader() && (t.getHeaderMediaUrl() == null || t.getHeaderMediaUrl().isBlank())) {
            throw new BusinessException("headerMediaUrl", "This template has a " + info.headerFormat().toLowerCase()
                    + " header. Enter a public https:// link to the " + info.headerFormat().toLowerCase() + ".");
        }
        return info;
    }

    private void apply(WhatsAppTemplate t, WhatsAppTemplateRequest r) {
        if (!t.getPurpose().isAutomatic()) {
            String label = ValidationPatterns.trimToNull(r.label());
            if (label == null) {
                throw new BusinessException("label", "Give this campaign template a name");
            }
            t.setLabel(label);
        }
        t.setTemplateName(ValidationPatterns.trimToNull(r.templateName()));
        String language = ValidationPatterns.trimToNull(r.templateLanguage());
        t.setTemplateLanguage(language == null ? "en_US" : language);
        t.setHeaderParams(clean(r.headerParams(), t.getPurpose(), "headerParams"));
        t.setBodyParams(clean(r.bodyParams(), t.getPurpose(), "bodyParams"));
        t.setButtonParams(clean(r.buttonParams(), t.getPurpose(), "buttonParams"));
        t.setHeaderMediaUrl(ValidationPatterns.trimToNull(r.headerMediaUrl()));
        if (r.enabled()) {
            if (t.getTemplateName() == null) {
                throw new BusinessException("templateName", "Choose a template name before switching this message on");
            }
            checkAgainstMeta(t, requireConnection());
        }
        t.setEnabled(r.enabled());
    }

    private static List<TemplateParam> clean(List<TemplateParam> params, TemplatePurpose purpose, String field) {
        if (params == null) {
            return List.of();
        }
        return params.stream().map(p -> {
            TemplateParamSource source;
            try {
                source = TemplateParamSource.valueOf(p.source());
            } catch (RuntimeException ex) {
                throw new BusinessException(field, "Choose what fills each placeholder");
            }
            if (!purpose.isAutomatic() && source.isAppointmentOnly()) {
                throw new BusinessException(field, "Appointment details aren't available in marketing messages");
            }
            String text = ValidationPatterns.trimToNull(p.text());
            if (source == TemplateParamSource.CUSTOM_TEXT && text == null) {
                throw new BusinessException(field, "Enter the text for each 'Custom text' placeholder");
            }
            if (text != null && text.length() > 500) {
                throw new BusinessException(field, "Custom text is limited to 500 characters");
            }
            return new TemplateParam(source.name(), source == TemplateParamSource.CUSTOM_TEXT ? text : null);
        }).toList();
    }

    private static void requireCount(String field, String part, int expected, List<TemplateParam> actual) {
        int got = actual == null ? 0 : actual.size();
        if (got != expected) {
            throw new BusinessException(field, "The template " + part + " needs " + expected + (expected == 1 ? " value" : " values")
                    + " but " + got + (got == 1 ? " is" : " are") + " set. Choose the template name again to refresh its placeholders.");
        }
    }

    private WhatsAppSettings requireConnection() {
        WhatsAppSettings s = settingsService.load();
        if (s.getPhoneNumberId() == null || s.getAccessToken() == null) {
            throw new BusinessException("Set up the WhatsApp connection (Phone number ID and access token) first.");
        }
        return s;
    }

    private WhatsAppTemplateName loadName(Long id) {
        return nameRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Template name", id));
    }

    private WhatsAppTemplate load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("WhatsApp template", id));
    }

    static WhatsAppTemplateResponse toResponse(WhatsAppTemplate t) {
        return new WhatsAppTemplateResponse(t.getId(), t.getPurpose(), t.getLabel(), t.isEnabled(), t.getTemplateName(),
                t.getTemplateLanguage(), t.getHeaderParams(), t.getBodyParams(), t.getButtonParams(), t.getHeaderMediaUrl(),
                t.getUpdatedAt());
    }

    private static MetaTemplateResponse toMeta(TemplateInfo i) {
        return new MetaTemplateResponse(i.name(), i.language(), i.status(), i.category(), i.headerFormat(), i.headerText(),
                i.headerParams(), i.bodyText(), i.bodyParams(), i.footerText(),
                i.buttons().stream().map(b -> new MetaButton(b.index(), b.type(), b.text(), b.needsValue())).toList());
    }
}
