package com.clinic.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Clinic-level information. Opening hours/working days describe the clinic itself (shown on the website);
 * bookable slots come from each doctor's own schedule. slotDurationMinutes is the default for new doctors.
 */
public record ClinicSettingsResponse(
        String clinicName,
        String address,
        String phone,
        String whatsappNumber,
        String email,
        String mapEmbedUrl,
        LocalTime openingTime,
        LocalTime closingTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        List<DayOfWeek> workingDays,
        Integer slotDurationMinutes,
        Integer bookingWindowDays,
        String timezone,
        String logoUrl,
        String tagline,
        String headerCtaText,
        String footerDescription,
        String copyrightText,
        String facebookUrl,
        String instagramUrl,
        String youtubeUrl,
        String googleReviewsUrl
) {
}
