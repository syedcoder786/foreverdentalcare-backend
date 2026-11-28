package com.clinic.dto.request;

import com.clinic.util.Slugs;
import com.clinic.util.ValidationPatterns;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record ServiceRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Pattern(regexp = "^$|" + Slugs.PATTERN, message = "Slug may contain lowercase letters, numbers and dashes")
        @Size(max = 120)
        String slug,

        @NotNull(message = "Specialty is required")
        Long specialtyId,

        @NotBlank(message = "Description is required")
        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @Size(max = 12, message = "At most 12 key points are allowed")
        List<@NotBlank @Size(max = 150, message = "Each key point must be at most 150 characters") String> details,

        @Size(max = 50, message = "Icon must be at most 50 characters")
        String icon,

        @Size(max = 500)
        @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL")
        String imageUrl,

        @DecimalMin(value = "0.0", message = "Fee cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Fee must have at most 2 decimal places")
        BigDecimal consultationFee,

        /** Optional time-limited offer on the fee: percent, start and end are given together or all left empty. */
        @Min(value = 1, message = "Offer must be at least 1%")
        @Max(value = 100, message = "Offer cannot exceed 100%")
        Integer offerDiscountPercent,

        LocalDate offerStartDate,

        LocalDate offerEndDate,

        @NotNull(message = "Estimated duration is required")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        @Max(value = 480, message = "Duration must be at most 480 minutes")
        Integer estimatedDuration,

        @Min(value = 0, message = "Display order cannot be negative")
        Integer displayOrder,

        Boolean active,

        /** Doctors allowed to receive bookings for this service. Null = leave assignments unchanged. */
        Set<Long> doctorIds
) {
}
