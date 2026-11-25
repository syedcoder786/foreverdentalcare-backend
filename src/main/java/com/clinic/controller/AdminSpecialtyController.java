package com.clinic.controller;

import com.clinic.dto.CatalogDtos.SpecialtyRequest;
import com.clinic.dto.CatalogDtos.SpecialtyResponse;
import com.clinic.dto.response.ApiResponse;
import com.clinic.service.SpecialtyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/specialties")
@RequiredArgsConstructor
public class AdminSpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    public ApiResponse<List<SpecialtyResponse>> list() {
        return ApiResponse.ok(specialtyService.listAll());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SpecialtyResponse>> create(@Valid @RequestBody SpecialtyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Specialty created", specialtyService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<SpecialtyResponse> update(@PathVariable Long id, @Valid @RequestBody SpecialtyRequest request) {
        return ApiResponse.ok("Specialty updated", specialtyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return switch (specialtyService.delete(id)) {
            case DELETED -> ApiResponse.ok("Specialty deleted", null);
            case DEACTIVATED -> ApiResponse.ok("Specialty is in use, so it was deactivated instead of deleted", null);
        };
    }
}
