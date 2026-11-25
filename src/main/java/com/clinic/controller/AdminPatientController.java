package com.clinic.controller;

import com.clinic.dto.AdminDtos.AuditLogResponse;
import com.clinic.dto.AdminDtos.PatientDetail;
import com.clinic.dto.AdminDtos.PatientSummary;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.service.AuditService;
import com.clinic.service.PatientAdminService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPatientController {

    private final PatientAdminService patientService;
    private final AuditService auditService;

    @GetMapping("/patients")
    public ApiResponse<PageResponse<PatientSummary>> patients(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(patientService.search(search, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/patients/{id}")
    public ApiResponse<PatientDetail> patient(@PathVariable Long id) {
        return ApiResponse.ok(patientService.get(id));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> auditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(auditService.list(page, size));
    }
}
