package com.clinic.mapper;

import com.clinic.dto.DoctorDtos.*;
import com.clinic.dto.response.ScheduleDtos;
import com.clinic.entity.*;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

@Component
public class DoctorMapper {

    public DoctorAdminResponse toAdmin(Doctor d) {
        return new DoctorAdminResponse(
                d.getId(), d.getFullName(), d.getSlug(), d.getProfessionalTitle(), d.getGender(), d.getProfileImageUrl(),
                d.getShortBio(), d.getBiography(), qualifications(d), d.getRegistrationNumber(),
                d.getRegistrationAuthority(), d.isCredentialsVerified(), d.getExperienceYears(), d.getConsultationFee(),
                d.getDisplayOrder(), d.isActive(), d.isFeatured(), specialties(d, false), services(d, false),
                weekly(d), d.getCreatedAt(), d.getUpdatedAt());
    }

    /** Public view: hides unverified credentials and inactive specialties/services. */
    public DoctorPublicResponse toPublic(Doctor d) {
        boolean verified = d.isCredentialsVerified();
        return new DoctorPublicResponse(
                d.getId(), d.getFullName(), d.getSlug(), d.getProfessionalTitle(), d.getGender(), d.getProfileImageUrl(),
                d.getShortBio(), d.getBiography(),
                verified ? qualifications(d) : List.of(),
                verified ? d.getRegistrationNumber() : null,
                verified ? d.getRegistrationAuthority() : null,
                verified, d.getExperienceYears(), d.getConsultationFee(), d.isFeatured(),
                specialties(d, true), services(d, true),
                weekly(d).stream().filter(DaySchedule::active).toList());
    }

    public ScheduleDtos.DoctorRef ref(Doctor d) {
        return new ScheduleDtos.DoctorRef(d.getId(), d.getFullName(), d.getSlug(), d.getProfessionalTitle());
    }

    /** Always 7 entries (Mon..Sun); days without a row appear as inactive. */
    public List<DaySchedule> weekly(Doctor d) {
        return java.util.Arrays.stream(DayOfWeek.values()).map(day -> d.getAvailability().stream()
                        .filter(a -> a.getDayOfWeek() == day).findFirst()
                        .map(a -> new DaySchedule(day, a.isActive(), a.getStartTime(), a.getEndTime(),
                                a.getBreakStartTime(), a.getBreakEndTime(), a.getSlotDurationMinutes()))
                        .orElse(new DaySchedule(day, false, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(18, 0),
                                null, null, 30)))
                .toList();
    }

    public List<ScheduleDtos.WeeklyDay> weeklyDays(Doctor d) {
        return weekly(d).stream().map(w -> new ScheduleDtos.WeeklyDay(d.getId(), w.dayOfWeek(), w.active(),
                w.startTime(), w.endTime(), w.breakStartTime(), w.breakEndTime(), w.slotDurationMinutes())).toList();
    }

    public List<SpecialHoursDto> specialHours(Doctor d) {
        return d.getSpecialHours().stream()
                .sorted(Comparator.comparing(DoctorSpecialHours::getSpecialDate))
                .map(s -> new SpecialHoursDto(s.getId(), s.getSpecialDate(), s.getStartTime(), s.getEndTime(),
                        s.getBreakStartTime(), s.getBreakEndTime(), s.getSlotDurationMinutes(), s.getNote()))
                .toList();
    }

    public ScheduleDtos.BlockView block(DoctorBlockedDate b) {
        Doctor d = b.getDoctor();
        return new ScheduleDtos.BlockView(b.getId(), d == null ? null : d.getId(), d == null ? null : d.getFullName(),
                d == null, b.getBlockDate(), b.getStartTime(), b.getEndTime(), b.getReason());
    }

    private static List<QualificationDto> qualifications(Doctor d) {
        return d.getQualifications().stream()
                .map(q -> new QualificationDto(q.degree(), q.institution(), q.year()))
                .toList();
    }

    private static List<SpecialtyRef> specialties(Doctor d, boolean activeOnly) {
        return d.getSpecialties().stream()
                .filter(s -> !activeOnly || s.isActive())
                .map(s -> new SpecialtyRef(s.getId(), s.getName(), s.getSlug()))
                .toList();
    }

    private static List<ServiceRef> services(Doctor d, boolean activeOnly) {
        return d.getServices().stream()
                .filter(s -> !activeOnly || (s.isActive() && s.getSpecialty().isActive()))
                .map(s -> new ServiceRef(s.getId(), s.getName(), s.getSlug(), s.getSpecialty().getId(),
                        s.getSpecialty().getName(), s.getEstimatedDuration(), s.getConsultationFee(), s.isActive()))
                .toList();
    }
}
