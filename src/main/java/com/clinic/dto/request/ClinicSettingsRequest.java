package com.clinic.dto.request;

import com.clinic.util.ValidationPatterns;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/** Cross-field rules (closing after opening, break within hours) are validated in ClinicSettingsService. */
public record ClinicSettingsRequest(
        @NotBlank(message = "Clinic name is required")
        @Size(max = 150)
        String clinicName,

        @NotBlank(message = "Address is required")
        @Size(max = 300)
        String address,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "Please enter a valid phone number")
        String phone,

        @Pattern(regexp = "^$|" + ValidationPatterns.PHONE, message = "Please enter a valid WhatsApp number")
        String whatsappNumber,

        @Email(message = "Please enter a valid email address")
        @Size(max = 150)
        String email,

        @Size(max = 1000)
        @Pattern(regexp = "^$|^https://www\\.google\\.com/maps/embed\\?.*$",
                message = "Map URL must be a Google Maps embed URL (https://www.google.com/maps/embed?...)")
        String mapEmbedUrl,

        @NotNull(message = "Opening time is required")
        LocalTime openingTime,

        @NotNull(message = "Closing time is required")
        LocalTime closingTime,

        LocalTime breakStartTime,

        LocalTime breakEndTime,

        @NotEmpty(message = "Select at least one working day")
        Set<DayOfWeek> workingDays,

        @NotNull(message = "Slot duration is required")
        @Min(value = 5, message = "Slot duration must be at least 5 minutes")
        @Max(value = 240, message = "Slot duration must be at most 240 minutes")
        Integer slotDurationMinutes,

        @NotNull(message = "Booking window is required")
        @Min(value = 1, message = "Booking window must be at least 1 day")
        @Max(value = 365, message = "Booking window must be at most 365 days")
        Integer bookingWindowDays,

        @Size(max = 500) @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL")
        String logoUrl,

        @Size(max = 150) String tagline,

        @Size(max = 50) String headerCtaText,

        @Size(max = 500) String footerDescription,

        @Size(max = 200) String copyrightText,

        @Size(max = 300) @Pattern(regexp = ValidationPatterns.WEB_URL, message = "Use a full https:// URL") String facebookUrl,

        @Size(max = 300) @Pattern(regexp = ValidationPatterns.WEB_URL, message = "Use a full https:// URL") String instagramUrl,

        @Size(max = 300) @Pattern(regexp = ValidationPatterns.WEB_URL, message = "Use a full https:// URL") String youtubeUrl,

        @Size(max = 300) @Pattern(regexp = ValidationPatterns.WEB_URL, message = "Use a full https:// URL") String googleReviewsUrl,

        /** Share link to the clinic on Google Maps (e.g. https://maps.app.goo.gl/...), used for "Get directions". */
        @Size(max = 500) @Pattern(regexp = ValidationPatterns.WEB_URL, message = "Use a full https:// URL") String mapLinkUrl,

        @Min(value = 1, message = "Discount must be at least 1%")
        @Max(value = 100, message = "Discount cannot exceed 100%")
        Integer firstVisitDiscountPercent,

        /** null keeps the current value (older clients that don't send it). */
        Boolean firstVisitOfferEnabled
) {
}
