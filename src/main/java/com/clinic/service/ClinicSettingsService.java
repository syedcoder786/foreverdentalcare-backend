package com.clinic.service;

import com.clinic.dto.request.ClinicSettingsRequest;
import com.clinic.dto.response.ClinicSettingsResponse;
import com.clinic.entity.ClinicSettings;
import com.clinic.exception.BusinessException;
import com.clinic.mapper.ClinicSettingsMapper;
import com.clinic.repository.ClinicSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class ClinicSettingsService {

    private final ClinicSettingsRepository repository;
    private final ClinicSettingsMapper mapper;

    @Transactional(readOnly = true)
    public ClinicSettings getSettings() {
        return repository.findById(ClinicSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Clinic settings row is missing. Check Flyway migrations."));
    }

    @Transactional(readOnly = true)
    public ClinicSettingsResponse getSettingsResponse() {
        return mapper.toResponse(getSettings());
    }

    @Transactional
    public ClinicSettingsResponse update(ClinicSettingsRequest request) {
        validate(request);
        ClinicSettings settings = getSettings();
        mapper.apply(request, settings);
        return mapper.toResponse(repository.save(settings));
    }

    private void validate(ClinicSettingsRequest r) {
        if (Boolean.TRUE.equals(r.firstVisitOfferEnabled()) && r.firstVisitDiscountPercent() == null) {
            throw new BusinessException("firstVisitDiscountPercent", "Set the first-visit discount percentage");
        }
        if (!r.closingTime().isAfter(r.openingTime())) {
            throw new BusinessException("closingTime", "Closing time must be after opening time");
        }
        long openMinutes = Duration.between(r.openingTime(), r.closingTime()).toMinutes();
        if (r.slotDurationMinutes() > openMinutes) {
            throw new BusinessException("slotDurationMinutes", "Slot duration is longer than the clinic's opening hours");
        }
        LocalTime bs = r.breakStartTime();
        LocalTime be = r.breakEndTime();
        if ((bs == null) != (be == null)) {
            throw new BusinessException("breakEndTime", "Provide both break start and break end, or neither");
        }
        if (bs != null) {
            if (!be.isAfter(bs)) {
                throw new BusinessException("breakEndTime", "Break end must be after break start");
            }
            if (bs.isBefore(r.openingTime()) || be.isAfter(r.closingTime())) {
                throw new BusinessException("breakStartTime", "Break must be within opening hours");
            }
        }
    }
}
