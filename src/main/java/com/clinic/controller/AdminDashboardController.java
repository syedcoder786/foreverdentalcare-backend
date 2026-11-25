package com.clinic.controller;

import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.DashboardResponse;
import com.clinic.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardResponse> overview() {
        return ApiResponse.ok(dashboardService.getOverview());
    }
}
