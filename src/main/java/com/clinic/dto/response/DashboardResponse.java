package com.clinic.dto.response;

import com.clinic.dto.AdminDtos;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        long totalAppointments,
        long pendingAppointments,
        long confirmedAppointments,
        long completedAppointments,
        long cancelledAppointments,
        long todaysAppointments,
        long totalDoctors,
        long totalSpecialties,
        long totalServices,
        LocalDate today,
        List<AppointmentResponse> todaysSchedule,
        List<AdminDtos.DoctorStats> byDoctor,
        List<AdminDtos.AuditLogResponse> recentActivity
) {
}
