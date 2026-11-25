package com.clinic.controller;

import com.clinic.dto.DoctorDtos.BlockedDateRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.ScheduleDtos.BlockView;
import com.clinic.dto.response.ScheduleDtos.CalendarRange;
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

/** Consolidated calendar (all doctors or one) and clinic-wide holidays. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final CalendarService calendarService;
    private final DoctorService doctorService;

    @GetMapping("/calendar")
    public ApiResponse<CalendarRange> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        return ApiResponse.ok(calendarService.range(from, to, doctorId));
    }

    @GetMapping("/holidays")
    public ApiResponse<List<BlockView>> holidays() {
        return ApiResponse.ok(doctorService.upcomingClinicHolidays());
    }

    /** Clinic-wide holiday: blocks every doctor's calendar. */
    @PostMapping("/holidays")
    public ResponseEntity<ApiResponse<List<BlockView>>> addHoliday(@Valid @RequestBody BlockedDateRequest request) {
        var result = doctorService.addBlock(null, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(AdminDoctorController.blockMessage(result.conflictingAppointments()), result.data()));
    }

    @DeleteMapping("/holidays/{id}")
    public ApiResponse<Void> deleteHoliday(@PathVariable Long id) {
        doctorService.deleteBlock(null, id);
        return ApiResponse.ok("Holiday removed", null);
    }
}
