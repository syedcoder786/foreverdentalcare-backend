package com.clinic.entity;

import com.clinic.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private DentalService service;

    /** Each appointment belongs to exactly one doctor's calendar. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /** Clinic-local date. */
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    /** Clinic-local start time. */
    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    /** Clinic-local end time (exclusive). The appointment occupies [appointmentTime, endTime) in the doctor's calendar. */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(length = 1000)
    private String message;

    /** Doctor's consultation fee at booking time. Null = discussed at consultation. */
    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private BigDecimal consultationFee;

    /** Set while this appointment carries the online first-visit discount (see FirstVisitOfferService). */
    @Column(name = "first_visit_discount_percent")
    private Integer firstVisitDiscountPercent;

    /** Treatment's own fee at booking time. Null = discussed at consultation. */
    @Column(name = "treatment_fee", precision = 10, scale = 2)
    private BigDecimal treatmentFee;

    /** Treatment offer percent, when an offer covered the appointment date at booking (or reschedule) time. */
    @Column(name = "treatment_discount_percent")
    private Integer treatmentDiscountPercent;

    /** Booked by the patient on the website (only these can carry the first-visit discount). */
    @Column(name = "online_booking", nullable = false)
    private boolean onlineBooking;

    /**
     * TRUE while the appointment occupies its slot, NULL when cancelled. Backed by the unique
     * constraint (appointment_date, appointment_time, slot_active) which prevents double booking.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "slot_active")
    private Boolean slotActive = Boolean.TRUE;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Total payable: consultation and treatment fees after their discounts. Null when neither fee is known. */
    public BigDecimal finalFee() {
        if (consultationFee == null && treatmentFee == null) {
            return null;
        }
        return discounted(consultationFee, firstVisitDiscountPercent).add(discounted(treatmentFee, treatmentDiscountPercent));
    }

    /** Fee after a percent discount, rounded to the whole rupee (same rounding as the website). */
    public static BigDecimal discounted(BigDecimal fee, Integer percent) {
        if (fee == null) {
            return BigDecimal.ZERO;
        }
        if (percent == null) {
            return fee;
        }
        return fee.multiply(BigDecimal.valueOf(100L - percent))
                .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
    }

    /** Changes status and keeps the slot-occupancy flag consistent. Transition rules are enforced by the service layer. */
    public void changeStatus(AppointmentStatus newStatus) {
        this.status = newStatus;
        this.slotActive = newStatus.occupiesSlot() ? Boolean.TRUE : null;
    }

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
}
