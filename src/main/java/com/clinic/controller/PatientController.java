package com.clinic.controller;

import com.clinic.dto.PatientDtos.PatientAppointmentResponse;
import com.clinic.dto.PatientDtos.PatientBookingRequest;
import com.clinic.dto.PatientDtos.PatientProfileRequest;
import com.clinic.dto.PatientDtos.PatientProfileResponse;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.BookingConfirmationResponse;
import com.clinic.security.PatientPrincipal;
import com.clinic.service.AppointmentService;
import com.clinic.service.PatientProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The signed-in patient's own profile and appointments (ROLE_PATIENT). */
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final PatientProfileService profileService;
    private final AppointmentService appointmentService;

    @GetMapping("/me")
    public ApiResponse<PatientProfileResponse> me(@AuthenticationPrincipal PatientPrincipal patient) {
        return ApiResponse.ok(profileService.get(patient.id()));
    }

    @PutMapping("/me")
    public ApiResponse<PatientProfileResponse> updateMe(@AuthenticationPrincipal PatientPrincipal patient,
                                                        @Valid @RequestBody PatientProfileRequest request) {
        return ApiResponse.ok("Profile saved", profileService.update(patient.id(), request));
    }

    @GetMapping("/appointments")
    public ApiResponse<List<PatientAppointmentResponse>> appointments(@AuthenticationPrincipal PatientPrincipal patient) {
        return ApiResponse.ok(appointmentService.listForPatient(patient.id()));
    }

    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<BookingConfirmationResponse>> book(@AuthenticationPrincipal PatientPrincipal patient,
                                                                         @Valid @RequestBody PatientBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                "Appointment request received. The clinic will confirm it shortly.",
                appointmentService.requestForPatient(patient.id(), request)));
    }
}
