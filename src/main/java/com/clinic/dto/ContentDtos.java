package com.clinic.dto;

import com.clinic.dto.response.ClinicSettingsResponse;
import com.clinic.util.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** Website content management models (navigation, page sections, SEO, testimonials, gallery, uploads). */
public final class ContentDtos {

    private ContentDtos() {
    }

    public static final String PAGE_KEY_PATTERN = "^(HOME|ABOUT|DOCTORS|SPECIALTIES|SERVICES|CONTACT|BOOK)$";
    public static final String SECTION_KEY_PATTERN = "^[a-z][a-z0-9_]{1,49}$";

    // ---------- navigation ----------
    public record NavigationItemDto(
            Long id,
            @NotBlank(message = "Label is required") @Size(max = 50) String label,
            @NotBlank(message = "Link is required") @Size(max = 300)
            @Pattern(regexp = ValidationPatterns.LINK_URL, message = "Use a path like /about or a full https:// URL") String url,
            @Min(0) Integer displayOrder,
            boolean active,
            boolean openInNewTab) {
    }

    public record NavigationRequest(@NotNull @Size(max = 12, message = "At most 12 menu items") List<@Valid NavigationItemDto> items) {
    }

    // ---------- sections ----------
    public record SectionItemDto(
            @Size(max = 50) String icon,
            @Size(max = 200) String title,
            @Size(max = 2000) String text,
            @Size(max = 500) @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL") String imageUrl) {
    }

    public record SectionRequest(
            @Pattern(regexp = SECTION_KEY_PATTERN, message = "Key: lowercase letters, numbers and underscores") String sectionKey,
            @Size(max = 200) String title,
            @Size(max = 500) String subtitle,
            @Size(max = 10000) String body,
            @Size(max = 500) @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL") String imageUrl,
            @Size(max = 50) String primaryButtonLabel,
            @Size(max = 300) @Pattern(regexp = ValidationPatterns.LINK_URL, message = "Use a path like /book or a full https:// URL") String primaryButtonUrl,
            @Size(max = 50) String secondaryButtonLabel,
            @Size(max = 300) @Pattern(regexp = ValidationPatterns.LINK_URL, message = "Use a path like /book or a full https:// URL") String secondaryButtonUrl,
            @Size(max = 30) List<@Valid SectionItemDto> items,
            @Min(0) Integer displayOrder,
            Boolean active) {
    }

    public record SectionResponse(Long id, String pageKey, String sectionKey, boolean core, String title, String subtitle,
                                  String body, String imageUrl, String primaryButtonLabel, String primaryButtonUrl,
                                  String secondaryButtonLabel, String secondaryButtonUrl, List<SectionItemDto> items,
                                  Integer displayOrder, boolean active, Instant updatedAt) {
    }

    // ---------- SEO ----------
    public record SeoRequest(
            @Size(max = 70, message = "Keep titles under 70 characters") String metaTitle,
            @Size(max = 170, message = "Keep descriptions under 170 characters") String metaDescription,
            @Size(max = 500) @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL") String ogImageUrl,
            boolean indexable) {
    }

    public record SeoResponse(String pageKey, String metaTitle, String metaDescription, String ogImageUrl, boolean indexable) {
    }

    public record PageContent(String pageKey, List<SectionResponse> sections, SeoResponse seo) {
    }

    public record SiteResponse(ClinicSettingsResponse clinic, List<NavigationItemDto> navigation) {
    }

    // ---------- testimonials ----------
    public record TestimonialRequest(
            @NotBlank(message = "Patient name or initials are required") @Size(max = 100) String patientName,
            @Size(max = 150) String context,
            @NotBlank(message = "Testimonial text is required") @Size(max = 2000) String quote,
            @Min(1) @Max(5) Integer rating,
            boolean consentConfirmed,
            boolean published,
            @Min(0) Integer displayOrder) {
    }

    public record TestimonialResponse(Long id, String patientName, String context, String quote, Integer rating,
                                      boolean consentConfirmed, boolean published, Integer displayOrder, Instant createdAt) {
    }

    // ---------- gallery ----------
    public record GalleryRequest(
            @NotBlank(message = "Image is required") @Size(max = 500)
            @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL") String imageUrl,
            @Size(max = 200) String caption,
            @Size(max = 50) String category,
            @Min(0) Integer displayOrder,
            Boolean active) {
    }

    public record GalleryResponse(Long id, String imageUrl, String caption, String category, Integer displayOrder,
                                  boolean active, Instant createdAt) {
    }

    public record UploadResponse(String url, String fileName, String contentType, long size) {
    }
}
