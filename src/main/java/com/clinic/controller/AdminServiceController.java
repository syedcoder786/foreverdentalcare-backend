package com.clinic.controller;

import com.clinic.dto.request.ServiceRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.ServiceResponse;
import com.clinic.service.DentalServiceCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/services")
@RequiredArgsConstructor
public class AdminServiceController {

    private final DentalServiceCatalogService catalogService;

    @GetMapping
    public ApiResponse<List<ServiceResponse>> list() {
        return ApiResponse.ok(catalogService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceResponse>> create(@Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Service created", catalogService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ServiceResponse> update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return ApiResponse.ok("Service updated", catalogService.update(id, request));
    }

    /** Deletes an unused service; services that have appointments are deactivated instead to preserve history. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return switch (catalogService.delete(id)) {
            case DELETED -> ApiResponse.ok("Service deleted", null);
            case DEACTIVATED -> ApiResponse.ok(
                    "Service has existing appointments, so it was deactivated instead of deleted", null);
        };
    }
}
