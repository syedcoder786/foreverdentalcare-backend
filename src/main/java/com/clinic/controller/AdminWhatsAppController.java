package com.clinic.controller;

import com.clinic.dto.WhatsAppDtos.WhatsAppSettingsRequest;
import com.clinic.dto.WhatsAppDtos.WhatsAppSettingsResponse;
import com.clinic.dto.WhatsAppDtos.WhatsAppTestRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.service.WhatsAppSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** WhatsApp Cloud API settings for patient sign-in codes (Admin -> Settings). */
@RestController
@RequestMapping("/api/admin/settings/whatsapp")
@RequiredArgsConstructor
public class AdminWhatsAppController {

    private final WhatsAppSettingsService service;

    @GetMapping
    public ApiResponse<WhatsAppSettingsResponse> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<WhatsAppSettingsResponse> update(@Valid @RequestBody WhatsAppSettingsRequest request) {
        return ApiResponse.ok("WhatsApp settings saved", service.update(request));
    }

    @PostMapping("/test")
    public ApiResponse<Void> test(@Valid @RequestBody WhatsAppTestRequest request) {
        return ApiResponse.ok(service.sendTest(request.phone()), null);
    }
}
