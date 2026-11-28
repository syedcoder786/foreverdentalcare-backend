package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A dental treatment offered by the clinic (table {@code services}).
 * Named DentalService to avoid confusion with Spring's {@code @Service} stereotype.
 */
@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
public class DentalService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** SEO-friendly URL segment, e.g. "root-canal-treatment". */
    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Doctors permitted to receive bookings for this service (owning side is Doctor.services). */
    @ManyToMany(mappedBy = "services")
    @OrderBy("displayOrder ASC, fullName ASC")
    private java.util.Set<Doctor> doctors = new java.util.LinkedHashSet<>();

    @Column(nullable = false, length = 1000)
    private String description;

    /** Pipe-separated key points, e.g. "Point one|Point two". */
    @Column(length = 2000)
    private String details;

    @Column(length = 50)
    private String icon;

    /** Null means the fee is discussed at consultation. */
    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private BigDecimal consultationFee;

    /** Time-limited percentage off the fee above; all three offer fields are set together or not at all. */
    @Column(name = "offer_discount_percent")
    private Integer offerDiscountPercent;

    /** First day of the offer (clinic-local, inclusive). */
    @Column(name = "offer_start_date")
    private java.time.LocalDate offerStartDate;

    /** Last day of the offer (clinic-local, inclusive). */
    @Column(name = "offer_end_date")
    private java.time.LocalDate offerEndDate;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private Integer estimatedDuration;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean hasOffer() {
        return offerDiscountPercent != null && offerStartDate != null && offerEndDate != null;
    }

    /** Percent off for an appointment on {@code date}, or null when no offer covers that date or there is no fee. */
    public Integer offerPercentFor(java.time.LocalDate date) {
        if (!hasOffer() || consultationFee == null || consultationFee.signum() <= 0) {
            return null;
        }
        return date.isBefore(offerStartDate) || date.isAfter(offerEndDate) ? null : offerDiscountPercent;
    }
}
