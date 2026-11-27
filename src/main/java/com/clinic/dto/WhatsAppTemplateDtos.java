package com.clinic.dto;

import com.clinic.entity.JsonValues.TemplateParam;
import com.clinic.entity.WhatsAppCampaign;
import com.clinic.enums.TemplatePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Admin WhatsApp message templates (automatic appointment messages, marketing) and campaigns. */
public final class WhatsAppTemplateDtos {

    private WhatsAppTemplateDtos() {
    }

    public record WhatsAppTemplateResponse(
            Long id,
            TemplatePurpose purpose,
            String label,
            boolean enabled,
            String templateName,
            String templateLanguage,
            List<TemplateParam> headerParams,
            List<TemplateParam> bodyParams,
            List<TemplateParam> buttonParams,
            String headerMediaUrl,
            Instant updatedAt
    ) {
    }

    public record WhatsAppTemplateRequest(
            /** Only read when adding: which event this is for, or MARKETING. */
            TemplatePurpose purpose,
            /** Name shown in the admin (marketing templates only; automatic ones keep their fixed label). */
            @Size(max = 100) String label,
            boolean enabled,
            @Size(max = 100) @Pattern(regexp = "^$|^[a-z0-9_]+$", message = "Template names use lowercase letters, numbers and underscores")
            String templateName,
            @Pattern(regexp = "^$|^[a-z]{2,3}(_[A-Z]{2})?$", message = "Use a language code like en_US, en or hi")
            String templateLanguage,
            @Size(max = 10) List<TemplateParam> headerParams,
            @Size(max = 30) List<TemplateParam> bodyParams,
            @Size(max = 10) List<TemplateParam> buttonParams,
            @Size(max = 1000) @Pattern(regexp = "^$|^https://\\S+$", message = "Use a public https:// link")
            String headerMediaUrl
    ) {
    }

    /** A saved, reusable template name. */
    public record TemplateNameResponse(Long id, String name, String language, boolean inUse) {
    }

    public record TemplateNameRequest(
            @NotBlank(message = "Enter the template name")
            @Size(max = 100) @Pattern(regexp = "^[a-z0-9_]+$", message = "Template names use lowercase letters, numbers and underscores")
            String name,
            @Pattern(regexp = "^$|^[a-z]{2,3}(_[A-Z]{2})?$", message = "Use a language code like en_US, en or hi")
            String language
    ) {
    }

    /** A template as it exists in the WhatsApp Business Account. */
    public record MetaTemplateResponse(
            String name, String language, String status, String category,
            String headerFormat, String headerText, int headerParams,
            String bodyText, int bodyParams, String footerText,
            List<MetaButton> buttons
    ) {
    }

    public record MetaButton(int index, String type, String text, boolean needsValue) {
    }

    public record CampaignRequest(@NotNull(message = "Choose a marketing template") Long templateId) {
    }

    public record CampaignResponse(
            Long id, String templateLabel, WhatsAppCampaign.Status status,
            int total, int sent, int failed, String createdBy, Instant createdAt, Instant finishedAt
    ) {
    }

    public record AudienceResponse(long optedInPatients) {
    }
}
