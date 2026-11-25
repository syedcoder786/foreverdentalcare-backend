package com.clinic.controller;

import com.clinic.dto.request.ClinicSettingsRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.ClinicSettingsResponse;
import com.clinic.service.ClinicSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final ClinicSettingsService settingsService;

    @GetMapping
    public ApiResponse<ClinicSettingsResponse> get() {
        return ApiResponse.ok(settingsService.getSettingsResponse());
    }

    @PutMapping
    public ApiResponse<ClinicSettingsResponse> update(@Valid @RequestBody ClinicSettingsRequest request) {
        return ApiResponse.ok("Settings saved", settingsService.update(request));
    }
}
