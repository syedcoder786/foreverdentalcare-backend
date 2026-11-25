package com.clinic.service;

import com.clinic.dto.AdminDtos.DoctorStats;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.DashboardResponse;
import com.clinic.enums.AppointmentStatus;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DentalServiceRepository serviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppointmentMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse getOverview() {
        LocalDate today = LocalDate.now(clock);
        List<AppointmentResponse> todays = appointmentRepository
                .findByAppointmentDateAndStatusNotOrderByAppointmentTimeAsc(today, AppointmentStatus.CANCELLED)
                .stream().map(mapper::toResponse).toList();

        Map<Long, long[]> counts = new LinkedHashMap<>();   // pending, confirmed, completed, cancelled
        Map<Long, String> names = new LinkedHashMap<>();
        doctorRepository.findAllByOrderByDisplayOrderAscFullNameAsc().forEach(d -> {
            counts.put(d.getId(), new long[4]);
            names.put(d.getId(), d.getFullName());
        });
        for (Object[] row : appointmentRepository.countByDoctorAndStatus()) {
            Long doctorId = (Long) row[0];
            long[] c = counts.computeIfAbsent(doctorId, k -> new long[4]);
            names.putIfAbsent(doctorId, (String) row[1]);
            c[((AppointmentStatus) row[2]).ordinal()] += (Long) row[3];
        }
        List<DoctorStats> byDoctor = counts.entrySet().stream().map(e -> {
            long[] c = e.getValue();
            long pending = c[AppointmentStatus.PENDING.ordinal()];
            long confirmed = c[AppointmentStatus.CONFIRMED.ordinal()];
            long cancelled = c[AppointmentStatus.CANCELLED.ordinal()];
            long completed = c[AppointmentStatus.COMPLETED.ordinal()];
            long todayCount = todays.stream().filter(a -> a.doctor().id().equals(e.getKey())).count();
            return new DoctorStats(e.getKey(), names.get(e.getKey()), pending, confirmed, completed, cancelled,
                    pending + confirmed + completed + cancelled, todayCount);
        }).toList();

        return new DashboardResponse(
                appointmentRepository.count(),
                appointmentRepository.countByStatus(AppointmentStatus.PENDING),
                appointmentRepository.countByStatus(AppointmentStatus.CONFIRMED),
                appointmentRepository.countByStatus(AppointmentStatus.COMPLETED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELLED),
                todays.size(),
                doctorRepository.countByActiveTrue(),
                specialtyRepository.countByActiveTrue(),
                serviceRepository.countByActiveTrue(),
                today,
                todays,
                byDoctor,
                auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 8)).stream()
                        .map(AuditService::toResponse).toList());
    }
}
