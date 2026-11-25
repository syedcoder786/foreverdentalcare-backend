package com.clinic.mapper;

import com.clinic.dto.request.ClinicSettingsRequest;
import com.clinic.dto.response.ClinicSettingsResponse;
import com.clinic.entity.ClinicSettings;
import com.clinic.util.ValidationPatterns;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.EnumSet;

@Component
public class ClinicSettingsMapper {

    private final ZoneId clinicZoneId;

    public ClinicSettingsMapper(ZoneId clinicZoneId) {
        this.clinicZoneId = clinicZoneId;
    }

    public ClinicSettingsResponse toResponse(ClinicSettings s) {
        return new ClinicSettingsResponse(
                s.getClinicName(),
                s.getAddress(),
                s.getPhone(),
                s.getWhatsappNumber(),
                s.getEmail(),
                s.getMapEmbedUrl(),
                s.getOpeningTime(),
                s.getClosingTime(),
                s.getBreakStartTime(),
                s.getBreakEndTime(),
                s.getWorkingDays().stream().sorted().toList(),
                s.getSlotDurationMinutes(),
                s.getBookingWindowDays(),
                clinicZoneId.getId(),
                s.getLogoUrl(),
                s.getTagline(),
                s.getHeaderCtaText(),
                s.getFooterDescription(),
                s.getCopyrightText(),
                s.getFacebookUrl(),
                s.getInstagramUrl(),
                s.getYoutubeUrl(),
                s.getGoogleReviewsUrl(),
                s.getMapLinkUrl());
    }

    public void apply(ClinicSettingsRequest r, ClinicSettings target) {
        target.setClinicName(r.clinicName().trim());
        target.setAddress(r.address().trim());
        target.setPhone(ValidationPatterns.normalizePhone(r.phone()));
        String whatsapp = ValidationPatterns.trimToNull(r.whatsappNumber());
        target.setWhatsappNumber(whatsapp == null ? null : ValidationPatterns.normalizePhone(whatsapp));
        target.setEmail(ValidationPatterns.trimToNull(r.email()));
        target.setMapEmbedUrl(ValidationPatterns.trimToNull(r.mapEmbedUrl()));
        target.setOpeningTime(r.openingTime());
        target.setClosingTime(r.closingTime());
        target.setBreakStartTime(r.breakStartTime());
        target.setBreakEndTime(r.breakEndTime());
        target.setWorkingDays(EnumSet.copyOf(r.workingDays()));
        target.setSlotDurationMinutes(r.slotDurationMinutes());
        target.setBookingWindowDays(r.bookingWindowDays());
        target.setLogoUrl(ValidationPatterns.trimToNull(r.logoUrl()));
        target.setTagline(ValidationPatterns.trimToNull(r.tagline()));
        target.setHeaderCtaText(ValidationPatterns.trimToNull(r.headerCtaText()));
        target.setFooterDescription(ValidationPatterns.trimToNull(r.footerDescription()));
        target.setCopyrightText(ValidationPatterns.trimToNull(r.copyrightText()));
        target.setFacebookUrl(ValidationPatterns.trimToNull(r.facebookUrl()));
        target.setInstagramUrl(ValidationPatterns.trimToNull(r.instagramUrl()));
        target.setYoutubeUrl(ValidationPatterns.trimToNull(r.youtubeUrl()));
        target.setGoogleReviewsUrl(ValidationPatterns.trimToNull(r.googleReviewsUrl()));
        target.setMapLinkUrl(ValidationPatterns.trimToNull(r.mapLinkUrl()));
    }
}
