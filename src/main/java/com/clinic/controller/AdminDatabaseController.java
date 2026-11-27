package com.clinic.controller;

import com.clinic.database.DatabaseDtos.ConnectionRequest;
import com.clinic.database.DatabaseDtos.StatusResponse;
import com.clinic.database.DatabaseDtos.SwitchRequest;
import com.clinic.database.DatabaseDtos.SwitchResponse;
import com.clinic.database.DatabaseDtos.TestResponse;
import com.clinic.database.DatabaseDtos.UseEnvironmentRequest;
import com.clinic.database.DatabaseSwitchService;
import com.clinic.dto.response.ApiResponse;
import com.clinic.security.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin -> Database: see, test and change the database the app uses, without a restart. */
@RestController
@RequestMapping("/api/admin/database")
@RequiredArgsConstructor
public class AdminDatabaseController {

    private final DatabaseSwitchService service;

    @GetMapping
    public ApiResponse<StatusResponse> status() {
        return ApiResponse.ok(service.status());
    }

    @PostMapping("/test")
    public ApiResponse<TestResponse> test(@Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok(service.test(request));
    }

    @PostMapping("/switch")
    public ApiResponse<SwitchResponse> switchDatabase(@Valid @RequestBody SwitchRequest request,
                                                      @AuthenticationPrincipal AdminPrincipal admin) {
        SwitchResponse result = service.switchTo(request, admin);
        return ApiResponse.ok(result.message(), result);
    }

    @PostMapping("/use-environment")
    public ApiResponse<SwitchResponse> useEnvironment(@Valid @RequestBody UseEnvironmentRequest request,
                                                      @AuthenticationPrincipal AdminPrincipal admin) {
        SwitchResponse result = service.useEnvironment(request, admin);
        return ApiResponse.ok(result.message(), result);
    }
}
