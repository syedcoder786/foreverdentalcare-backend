package com.clinic.dto;

import com.clinic.dto.response.ScheduleDtos;
import com.clinic.enums.Gender;
import com.clinic.util.Slugs;
import com.clinic.util.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Request and response models for doctors, their schedules and blocked time. */
public final class DoctorDtos {

    private DoctorDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record QualificationDto(
            @NotBlank(message = "Degree is required") @Size(max = 150) String degree,
            @Size(max = 150) String institution,
            @Size(max = 20) String year) {
    }

    public record DoctorRequest(
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @Pattern(regexp = "^$|" + Slugs.PATTERN, message = "Slug may contain lowercase letters, numbers and dashes")
            @Size(max = 120) String slug,
            @NotBlank(message = "Professional title is required") @Size(max = 150) String professionalTitle,
            Gender gender,
            @Size(max = 500) @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL")
            String profileImageUrl,
            @Size(max = 500) String shortBio,
            @Size(max = 10000) String biography,
            @Size(max = 15) List<@Valid QualificationDto> qualifications,
            @Size(max = 100) String registrationNumber,
            @Size(max = 200) String registrationAuthority,
            boolean credentialsVerified,
            @Min(0) @Max(80) Integer experienceYears,
            @DecimalMin(value = "0.0", message = "Fee cannot be negative") @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
            @Min(0) Integer displayOrder,
            Boolean active,
            Boolean featured,
            Set<Long> specialtyIds,
            Set<Long> serviceIds) {
    }

    public record DaySchedule(
            @NotNull(message = "Day is required") DayOfWeek dayOfWeek,
            boolean active,
            @NotNull(message = "Start time is required") LocalTime startTime,
            @NotNull(message = "End time is required") LocalTime endTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime,
            @NotNull @Min(value = 5, message = "Slot must be at least 5 minutes")
            @Max(value = 240, message = "Slot must be at most 240 minutes") Integer slotDurationMinutes) {
    }

    public record SpecialHoursDto(
            Long id,
            @NotNull(message = "Date is required") LocalDate date,
            @NotNull(message = "Start time is required") LocalTime startTime,
            @NotNull(message = "End time is required") LocalTime endTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime,
            @NotNull @Min(5) @Max(240) Integer slotDurationMinutes,
            @Size(max = 200) String note) {
    }

    /** Full replacement of a doctor's weekly schedule and date-specific hours. */
    public record ScheduleRequest(
            @NotNull @Size(max = 7) List<@Valid DaySchedule> weekly,
            @Size(max = 200) List<@Valid SpecialHoursDto> specialHours) {
    }

    /** Blocks a whole day (no times) or a time range, optionally for a range of dates (inclusive, max 90 days). */
    public record BlockedDateRequest(
            @NotNull(message = "Date is required") LocalDate date,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            @Size(max = 200) String reason) {
    }

    // ---------------------------------------------------------------- responses

    public record SpecialtyRef(Long id, String name, String slug) {
    }

    public record ServiceRef(Long id, String name, String slug, Long specialtyId, String specialtyName,
                             Integer estimatedDuration, BigDecimal consultationFee, boolean active) {
    }

    public record ScheduleResponse(Long doctorId, List<DaySchedule> weekly, List<SpecialHoursDto> specialHours,
                                   List<ScheduleDtos.BlockView> upcomingBlocks) {
    }

    /** Full doctor record for the admin dashboard. */
    public record DoctorAdminResponse(
            Long id, String fullName, String slug, String professionalTitle, Gender gender, String profileImageUrl,
            String shortBio, String biography, List<QualificationDto> qualifications, String registrationNumber,
            String registrationAuthority, boolean credentialsVerified, Integer experienceYears,
            BigDecimal consultationFee, Integer displayOrder, boolean active, boolean featured,
            List<SpecialtyRef> specialties, List<ServiceRef> services, List<DaySchedule> weekly,
            Instant createdAt, Instant updatedAt) {
    }

    /**
     * Public profile. Qualifications and registration details are included ONLY when an admin has marked the
     * credentials as verified, so unverified claims are never published.
     */
    public record DoctorPublicResponse(
            Long id, String fullName, String slug, String professionalTitle, Gender gender, String profileImageUrl,
            String shortBio, String biography, List<QualificationDto> qualifications, String registrationNumber,
            String registrationAuthority, boolean credentialsVerified, Integer experienceYears,
            BigDecimal consultationFee, boolean featured, List<SpecialtyRef> specialties, List<ServiceRef> services,
            List<DaySchedule> weekly) {
    }
}
