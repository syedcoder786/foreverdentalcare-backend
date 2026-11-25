package com.clinic.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ServiceResponse(
        Long id,
        String name,
        String slug,
        String description,
        List<String> details,
        String icon,
        String imageUrl,
        BigDecimal consultationFee,
        Integer estimatedDuration,
        Integer displayOrder,
        boolean active,
        SpecialtySummary specialty,
        List<DoctorSummary> doctors
) {
    public record SpecialtySummary(Long id, String name, String slug) {
    }

    public record DoctorSummary(Long id, String fullName, String slug, String professionalTitle) {
    }
}
