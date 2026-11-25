package com.clinic.controller;

import com.clinic.dto.CatalogDtos.DoctorAppointments;
import com.clinic.dto.DoctorDtos.*;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.ScheduleDtos.AdminDaySchedule;
import com.clinic.dto.response.ScheduleDtos.BlockView;
import com.clinic.service.CalendarService;
import com.clinic.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Doctor profiles, independent schedules, blocked time and per-doctor calendars. */
@RestController
@RequestMapping("/api/admin/doctors")
@RequiredArgsConstructor
public class AdminDoctorController {

    private final DoctorService doctorService;
    private final CalendarService calendarService;

    @GetMapping
    public ApiResponse<List<DoctorAdminResponse>> list() {
        return ApiResponse.ok(doctorService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<DoctorAdminResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(doctorService.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorAdminResponse>> create(@Valid @RequestBody DoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Doctor created", doctorService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<DoctorAdminResponse> update(@PathVariable Long id, @Valid @RequestBody DoctorRequest request) {
        return ApiResponse.ok("Doctor updated", doctorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return switch (doctorService.delete(id)) {
            case DELETED -> ApiResponse.ok("Doctor deleted", null);
            case DEACTIVATED -> ApiResponse.ok("Doctor has appointment history, so the profile was deactivated instead of deleted", null);
        };
    }

    // ---------- schedule ----------

    @GetMapping("/{id}/schedule")
    public ApiResponse<ScheduleResponse> schedule(@PathVariable Long id) {
        return ApiResponse.ok(doctorService.getSchedule(id));
    }

    @PutMapping("/{id}/schedule")
    public ApiResponse<ScheduleResponse> updateSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        var result = doctorService.updateSchedule(id, request);
        String message = result.conflictingAppointments() == 0 ? "Schedule saved"
                : "Schedule saved. " + result.conflictingAppointments()
                + " upcoming appointment(s) are now outside this doctor's hours - please review and reschedule them.";
        return ApiResponse.ok(message, result.data());
    }

    @PostMapping("/{id}/blocked-dates")
    public ResponseEntity<ApiResponse<List<BlockView>>> block(@PathVariable Long id, @Valid @RequestBody BlockedDateRequest request) {
        var result = doctorService.addBlock(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(blockMessage(result.conflictingAppointments()), result.data()));
    }

    @DeleteMapping("/{id}/blocked-dates/{blockId}")
    public ApiResponse<Void> unblock(@PathVariable Long id, @PathVariable Long blockId) {
        doctorService.deleteBlock(id, blockId);
        return ApiResponse.ok("Blocked time removed", null);
    }

    // ---------- calendars ----------

    @GetMapping("/{id}/appointments")
    public ApiResponse<DoctorAppointments> appointments(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(calendarService.doctorAppointments(id, from, to));
    }

    /** One doctor's day: every slot (available / booked / blocked / past), appointments and blocks. */
    @GetMapping("/{id}/day")
    public ApiResponse<AdminDaySchedule> day(@PathVariable Long id,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(calendarService.adminDay(id, date));
    }

    static String blockMessage(int conflicts) {
        return conflicts == 0 ? "Time blocked"
                : "Time blocked. " + conflicts + " existing appointment(s) fall in this period - please contact the patients and reschedule.";
    }
}
