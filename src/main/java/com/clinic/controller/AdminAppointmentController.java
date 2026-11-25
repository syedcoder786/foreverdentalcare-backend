package com.clinic.controller;

import com.clinic.dto.AdminDtos;
import com.clinic.dto.request.RescheduleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.clinic.dto.request.StatusUpdateRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.enums.AppointmentStatus;
import com.clinic.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
public class AdminAppointmentController {

    /** Whitelisted sort fields (API name -> entity path). */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "appointmentDate", "appointmentDate",
            "createdAt", "createdAt",
            "status", "status",
            "doctorName", "doctor.fullName",
            "patientName", "patient.fullName");
    private static final String DEFAULT_SORT = "appointmentDate";

    private final AppointmentService appointmentService;

    /**
     * List appointments with optional filters.
     * Example: GET /api/admin/appointments?search=john&status=PENDING&date=2026-12-01&page=0&size=20&sortBy=createdAt&sortDir=desc
     * {@code date} filters a single day; {@code from}/{@code to} filter a range.
     */
    @GetMapping
    public ApiResponse<PageResponse<AppointmentResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "appointmentDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        String field = SORT_FIELDS.getOrDefault(sortBy, DEFAULT_SORT);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, field);
        if (field.equals("appointmentDate")) {
            sort = sort.and(Sort.by(direction, "appointmentTime"));
        }
        sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        LocalDate effectiveFrom = date != null ? date : from;
        LocalDate effectiveTo = date != null ? date : to;
        return ApiResponse.ok(appointmentService.search(search, status, doctorId, specialtyId, effectiveFrom, effectiveTo,
                PageRequest.of(page, size, sort)));
    }

    /** Manual booking by clinic staff, e.g. for a phone call. Same per-doctor availability rules apply. */
    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(@Valid @RequestBody AdminDtos.AdminAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Appointment created", appointmentService.createByAdmin(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppointmentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.get(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AppointmentResponse> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.ok("Appointment status updated", appointmentService.updateStatus(id, request.status()));
    }

    @PatchMapping("/{id}/reschedule")
    public ApiResponse<AppointmentResponse> reschedule(@PathVariable Long id,
                                                       @Valid @RequestBody RescheduleRequest request) {
        return ApiResponse.ok("Appointment rescheduled", appointmentService.reschedule(id, request));
    }
}
