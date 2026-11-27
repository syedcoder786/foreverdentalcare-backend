package com.clinic.controller;

import com.clinic.dto.response.ApiResponse;
import com.clinic.security.AdminPrincipal;
import com.clinic.storage.StorageDtos.CloudinaryRequest;
import com.clinic.storage.StorageDtos.SaveRequest;
import com.clinic.storage.StorageDtos.SaveResponse;
import com.clinic.storage.StorageDtos.StatusResponse;
import com.clinic.storage.StorageDtos.TestResponse;
import com.clinic.storage.StorageDtos.UseEnvironmentRequest;
import com.clinic.storage.StorageSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin -> Image storage: see, test and change where uploaded images are stored, without a restart. */
@RestController
@RequestMapping("/api/admin/storage")
@RequiredArgsConstructor
public class AdminStorageController {

    private final StorageSettingsService service;

    @GetMapping
    public ApiResponse<StatusResponse> status() {
        return ApiResponse.ok(service.status());
    }

    @PostMapping("/test")
    public ApiResponse<TestResponse> test(@Valid @RequestBody CloudinaryRequest request) {
        return ApiResponse.ok(service.test(request));
    }

    @PutMapping
    public ApiResponse<SaveResponse> save(@Valid @RequestBody SaveRequest request,
                                          @AuthenticationPrincipal AdminPrincipal admin) {
        SaveResponse result = service.save(request, admin);
        return ApiResponse.ok(result.message(), result);
    }

    @PostMapping("/use-environment")
    public ApiResponse<SaveResponse> useEnvironment(@Valid @RequestBody UseEnvironmentRequest request,
                                                    @AuthenticationPrincipal AdminPrincipal admin) {
        SaveResponse result = service.useEnvironment(request, admin);
        return ApiResponse.ok(result.message(), result);
    }
}
