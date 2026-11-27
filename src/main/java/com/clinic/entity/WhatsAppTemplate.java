package com.clinic.entity;

import com.clinic.enums.TemplatePurpose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** An approved Meta template used for one purpose, with what fills each of its placeholders. */
@Entity
@Table(name = "whatsapp_templates")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TemplatePurpose purpose;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Column(name = "template_language", nullable = false, length = 20)
    private String templateLanguage = "en_US";

    @Convert(converter = JsonValues.TemplateParamListConverter.class)
    @Column(name = "header_params", nullable = false, length = 4000)
    private List<JsonValues.TemplateParam> headerParams = new ArrayList<>();

    @Convert(converter = JsonValues.TemplateParamListConverter.class)
    @Column(name = "body_params", nullable = false, length = 4000)
    private List<JsonValues.TemplateParam> bodyParams = new ArrayList<>();

    /** One entry per button that takes a value, in button order. */
    @Convert(converter = JsonValues.TemplateParamListConverter.class)
    @Column(name = "button_params", nullable = false, length = 4000)
    private List<JsonValues.TemplateParam> buttonParams = new ArrayList<>();

    /** Public image/video/document URL for templates with a media header. */
    @Column(name = "header_media_url", length = 1000)
    private String headerMediaUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
