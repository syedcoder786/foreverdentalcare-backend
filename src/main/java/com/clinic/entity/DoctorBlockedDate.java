package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Unavailable time. startTime/endTime null = whole day blocked. doctor null = clinic-wide holiday for every doctor.
 */
@Entity
@Table(name = "doctor_blocked_dates")
@Getter
@Setter
@NoArgsConstructor
public class DoctorBlockedDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @Column(name = "block_date", nullable = false)
    private LocalDate blockDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isFullDay() {
        return startTime == null;
    }

    /** True if [start, end) intersects this block. */
    public boolean overlaps(LocalTime start, LocalTime end) {
        return isFullDay() || (start.isBefore(endTime) && end.isAfter(startTime));
    }
}
