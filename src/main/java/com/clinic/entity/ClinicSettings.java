package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/** Single-row table (id = 1) holding configurable clinic information and scheduling rules. */
@Entity
@Table(name = "clinic_settings")
@Getter
@Setter
@NoArgsConstructor
public class ClinicSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "clinic_name", nullable = false, length = 150)
    private String clinicName;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Column(length = 150)
    private String email;

    @Column(name = "map_embed_url", length = 1000)
    private String mapEmbedUrl;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    @Convert(converter = DayOfWeekSetConverter.class)
    @Column(name = "working_days", nullable = false, length = 100)
    private Set<DayOfWeek> workingDays = EnumSet.noneOf(DayOfWeek.class);

    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes;

    @Column(name = "booking_window_days", nullable = false)
    private Integer bookingWindowDays;

    // ---- Branding, header and footer (editable in Admin -> Clinic settings) ----
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 150)
    private String tagline;

    @Column(name = "header_cta_text", length = 50)
    private String headerCtaText;

    @Column(name = "footer_description", length = 500)
    private String footerDescription;

    @Column(name = "copyright_text", length = 200)
    private String copyrightText;

    @Column(name = "facebook_url", length = 300)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 300)
    private String youtubeUrl;

    @Column(name = "google_reviews_url", length = 300)
    private String googleReviewsUrl;

    @Column(name = "map_link_url", length = 500)
    private String mapLinkUrl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
