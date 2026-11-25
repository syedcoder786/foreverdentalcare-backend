package com.clinic.dto;

import com.clinic.dto.response.AppointmentResponse;
import com.clinic.enums.AppointmentStatus;
import com.clinic.util.ValidationPatterns;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Admin-only models: manual appointments, patients, audit log. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Appointment created by clinic staff (e.g. phone booking). Same availability rules as public bookings. */
    public record AdminAppointmentRequest(
            @NotNull(message = "Doctor is required") Long doctorId,
            @NotNull(message = "Service is required") Long serviceId,
            @NotNull(message = "Date is required") LocalDate appointmentDate,
            @NotNull(message = "Time is required") LocalTime appointmentTime,
            @NotBlank(message = "Patient name is required") @Size(min = 2, max = 100) String fullName,
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = ValidationPatterns.PHONE, message = "Please enter a valid phone number") String phone,
            @Email @Size(max = 150) String email,
            @Size(max = 1000) String message,
            AppointmentStatus initialStatus) {
    }

    public record PatientSummary(Long id, String fullName, String phone, String email, Instant createdAt,
                                 long appointmentCount) {
    }

    public record PatientDetail(PatientSummary patient, List<AppointmentResponse> appointments) {
    }

    public record AuditLogResponse(Long id, String adminEmail, String action, String entityType, String entityId,
                                   String summary, Instant createdAt) {
    }

    public record DoctorStats(Long doctorId, String doctorName, long pending, long confirmed, long completed,
                              long cancelled, long total, long today) {
    }
}
