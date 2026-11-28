package com.clinic.service;

import com.clinic.dto.DoctorDtos.*;
import com.clinic.dto.response.ScheduleDtos.BlockView;
import com.clinic.entity.*;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ConflictException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.DoctorMapper;
import com.clinic.repository.*;
import com.clinic.util.Slugs;
import com.clinic.util.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DoctorService {

    public enum DeleteOutcome { DELETED, DEACTIVATED }

    /** Result of a change that may leave existing appointments outside the doctor's availability. */
    public record ChangeResult<T>(T data, int conflictingAppointments) {
    }

    private static final int MAX_BLOCK_RANGE_DAYS = 90;

    private final DoctorRepository repository;
    private final SpecialtyRepository specialtyRepository;
    private final DentalServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorBlockedDateRepository blockedRepository;
    private final ClinicSettingsService settingsService;
    private final ScheduleEngine scheduleEngine;
    private final DoctorMapper mapper;
    private final AuditService audit;

    // ------------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public List<DoctorPublicResponse> listPublic(Long specialtyId) {
        return repository.findAllByActiveTrueOrderByDisplayOrderAscFullNameAsc().stream()
                .filter(d -> specialtyId == null || d.getSpecialties().stream().anyMatch(s -> s.getId().equals(specialtyId)))
                .map(mapper::toPublic)
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorPublicResponse getPublic(Long id) {
        return mapper.toPublic(loadActive(id));
    }

    @Transactional(readOnly = true)
    public DoctorPublicResponse getPublicBySlug(String slug) {
        Doctor d = repository.findBySlug(slug).filter(Doctor::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", slug));
        return mapper.toPublic(d);
    }

    /** Only services assigned to this doctor (and active) are bookable with them. */
    @Transactional(readOnly = true)
    public List<ServiceRef> publicServices(Long doctorId, Long specialtyId) {
        return mapper.toPublic(loadActive(doctorId)).services().stream()
                .filter(s -> specialtyId == null || s.specialtyId().equals(specialtyId))
                .toList();
    }

    // ------------------------------------------------------------------ admin CRUD

    @Transactional(readOnly = true)
    public List<DoctorAdminResponse> listAll() {
        return repository.findAllByOrderByDisplayOrderAscFullNameAsc().stream().map(mapper::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public DoctorAdminResponse get(Long id) {
        return mapper.toAdmin(load(id));
    }

    @Transactional
    public DoctorAdminResponse create(DoctorRequest r) {
        Doctor d = new Doctor();
        apply(r, d);
        d.setSlug(Slugs.unique(r.slug(), r.fullName(), repository::existsBySlug));
        // Sensible default schedule (Mon-Sat 10:00-18:00) so the doctor is usable immediately; edit in Schedule.
        int slot = settingsService.getSettings().getSlotDurationMinutes();
        for (DayOfWeek day : DayOfWeek.values()) {
            DoctorAvailability a = new DoctorAvailability();
            a.setDoctor(d);
            a.setDayOfWeek(day);
            a.setStartTime(LocalTime.of(10, 0));
            a.setEndTime(LocalTime.of(18, 0));
            a.setSlotDurationMinutes(slot);
            a.setActive(day != DayOfWeek.SUNDAY);
            d.getAvailability().add(a);
        }
        Doctor saved = repository.save(d);
        audit.record("CREATE", "Doctor", saved.getId(), saved.getFullName());
        return mapper.toAdmin(saved);
    }

    @Transactional
    public DoctorAdminResponse update(Long id, DoctorRequest r) {
        Doctor d = load(id);
        apply(r, d);
        if (r.slug() != null && !r.slug().isBlank() && !r.slug().equals(d.getSlug())) {
            if (repository.existsBySlugAndIdNot(r.slug(), id)) {
                throw new ConflictException("This URL slug is already used by another doctor");
            }
            d.setSlug(r.slug());
        }
        audit.record("UPDATE", "Doctor", id, d.getFullName() + (d.isCredentialsVerified() ? " (credentials verified)" : ""));
        return mapper.toAdmin(repository.save(d));
    }

    /** Deletes a doctor without appointment history; otherwise deactivates (history and calendars are preserved). */
    @Transactional
    public DeleteOutcome delete(Long id) {
        Doctor d = load(id);
        if (appointmentRepository.existsByDoctorId(id)) {
            d.setActive(false);
            audit.record("DEACTIVATE", "Doctor", id, d.getFullName());
            return DeleteOutcome.DEACTIVATED;
        }
        repository.delete(d);
        audit.record("DELETE", "Doctor", id, d.getFullName());
        return DeleteOutcome.DELETED;
    }

    private void apply(DoctorRequest r, Doctor d) {
        d.setFullName(r.fullName().trim());
        d.setProfessionalTitle(r.professionalTitle().trim());
        d.setGender(r.gender() == null ? com.clinic.enums.Gender.UNSPECIFIED : r.gender());
        d.setProfileImageUrl(ValidationPatterns.trimToNull(r.profileImageUrl()));
        d.setShortBio(ValidationPatterns.trimToNull(r.shortBio()));
        d.setBiography(ValidationPatterns.trimToNull(r.biography()));
        d.setQualifications(new ArrayList<>(r.qualifications() == null ? List.of() : r.qualifications().stream()
                .map(q -> new JsonValues.Qualification(q.degree().trim(), ValidationPatterns.trimToNull(q.institution()),
                        ValidationPatterns.trimToNull(q.year())))
                .toList()));
        d.setRegistrationNumber(ValidationPatterns.trimToNull(r.registrationNumber()));
        d.setRegistrationAuthority(ValidationPatterns.trimToNull(r.registrationAuthority()));
        d.setCredentialsVerified(r.credentialsVerified());
        d.setExperienceYears(r.experienceYears());
        d.setConsultationFee(r.consultationFee());
        d.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
        if (r.active() != null) {
            d.setActive(r.active());
        }
        if (r.featured() != null) {
            d.setFeatured(r.featured());
        }
        if (r.specialtyIds() != null) {
            Set<Specialty> specialties = new LinkedHashSet<>(specialtyRepository.findAllById(r.specialtyIds()));
            if (specialties.size() != r.specialtyIds().size()) {
                throw new BusinessException("specialtyIds", "One or more selected specialties do not exist");
            }
            d.setSpecialties(specialties);
        }
        if (r.serviceIds() != null) {
            Set<DentalService> services = new LinkedHashSet<>(serviceRepository.findAllById(r.serviceIds()));
            if (services.size() != r.serviceIds().size()) {
                throw new BusinessException("serviceIds", "One or more selected services do not exist");
            }
            d.setServices(services);
        }
        requireServicesWithinSpecialties(d);
    }

    /**
     * A doctor may only be assigned services from their own specialties (e.g. dental treatments cannot be attached to a
     * Unani practitioner unless she is explicitly given the Dentistry specialty).
     */
    static void requireServicesWithinSpecialties(Doctor d) {
        Set<Long> specialtyIds = new HashSet<>();
        d.getSpecialties().forEach(s -> specialtyIds.add(s.getId()));
        List<String> outside = d.getServices().stream()
                .filter(s -> !specialtyIds.contains(s.getSpecialty().getId()))
                .map(DentalService::getName)
                .toList();
        if (!outside.isEmpty()) {
            throw new BusinessException("serviceIds",
                    "These services are outside the doctor's specialties: " + String.join(", ", outside)
                            + ". Add the matching specialty to the doctor first.");
        }
    }

    // ------------------------------------------------------------------ schedule

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(Long doctorId) {
        Doctor d = load(doctorId);
        return scheduleResponse(d);
    }

    /**
     * Replaces the doctor's weekly schedule and date-specific hours. Rows are updated in place (not delete+insert) so the
     * per-day unique constraints are never violated mid-flush. Only this doctor's calendar is affected.
     */
    @Transactional
    public ChangeResult<ScheduleResponse> updateSchedule(Long doctorId, ScheduleRequest r) {
        Doctor d = repository.findByIdForUpdate(doctorId).orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (DaySchedule day : r.weekly()) {
            if (!seen.add(day.dayOfWeek())) {
                throw new BusinessException("weekly", day.dayOfWeek() + " appears more than once");
            }
            validateHours(day.startTime(), day.endTime(), day.breakStartTime(), day.breakEndTime(),
                    day.slotDurationMinutes(), "weekly." + day.dayOfWeek());
        }
        Map<DayOfWeek, DoctorAvailability> existing = new EnumMap<>(DayOfWeek.class);
        d.getAvailability().forEach(a -> existing.put(a.getDayOfWeek(), a));
        for (DaySchedule day : r.weekly()) {
            DoctorAvailability a = existing.computeIfAbsent(day.dayOfWeek(), k -> {
                DoctorAvailability created = new DoctorAvailability();
                created.setDoctor(d);
                created.setDayOfWeek(k);
                d.getAvailability().add(created);
                return created;
            });
            a.setActive(day.active());
            a.setStartTime(day.startTime());
            a.setEndTime(day.endTime());
            a.setBreakStartTime(day.breakStartTime());
            a.setBreakEndTime(day.breakEndTime());
            a.setSlotDurationMinutes(day.slotDurationMinutes());
        }
        // Days omitted from the request become weekly off days.
        d.getAvailability().stream().filter(a -> !seen.contains(a.getDayOfWeek())).forEach(a -> a.setActive(false));

        List<SpecialHoursDto> special = r.specialHours() == null ? List.of() : r.specialHours();
        Set<LocalDate> dates = new HashSet<>();
        for (SpecialHoursDto s : special) {
            if (!dates.add(s.date())) {
                throw new BusinessException("specialHours", "Special hours for " + s.date() + " appear more than once");
            }
            validateHours(s.startTime(), s.endTime(), s.breakStartTime(), s.breakEndTime(), s.slotDurationMinutes(),
                    "specialHours." + s.date());
        }
        Map<LocalDate, DoctorSpecialHours> existingSpecial = new HashMap<>();
        d.getSpecialHours().forEach(s -> existingSpecial.put(s.getSpecialDate(), s));
        d.getSpecialHours().removeIf(s -> !dates.contains(s.getSpecialDate()));
        for (SpecialHoursDto s : special) {
            DoctorSpecialHours h = existingSpecial.get(s.date());
            if (h == null) {
                h = new DoctorSpecialHours();
                h.setDoctor(d);
                h.setSpecialDate(s.date());
                d.getSpecialHours().add(h);
            }
            h.setStartTime(s.startTime());
            h.setEndTime(s.endTime());
            h.setBreakStartTime(s.breakStartTime());
            h.setBreakEndTime(s.breakEndTime());
            h.setSlotDurationMinutes(s.slotDurationMinutes());
            h.setNote(ValidationPatterns.trimToNull(s.note()));
        }
        repository.saveAndFlush(d);
        int conflicts = countUpcomingOutsideAvailability(d);
        audit.record("UPDATE", "DoctorSchedule", doctorId, d.getFullName() + " schedule updated");
        return new ChangeResult<>(scheduleResponse(d), conflicts);
    }

    private static void validateHours(LocalTime start, LocalTime end, LocalTime bs, LocalTime be, int slot, String field) {
        if (!end.isAfter(start)) {
            throw new BusinessException(field, "End time must be after start time (" + field + ")");
        }
        if ((bs == null) != (be == null)) {
            throw new BusinessException(field, "Enter both the morning shift end and the evening shift start, or turn off the evening shift (" + field + ")");
        }
        if (bs != null) {
            if (!be.isAfter(bs) || !bs.isAfter(start) || !be.isBefore(end)) {
                throw new BusinessException(field, "Shift times are out of order: the morning shift must end before the evening shift starts (" + field + ")");
            }
        }
        if (start.plusMinutes(slot).isAfter(end) || start.plusMinutes(slot).isBefore(start)) {
            throw new BusinessException(field, "Appointment duration is longer than the working hours (" + field + ")");
        }
    }

    // ------------------------------------------------------------------ blocked time

    @Transactional
    public ChangeResult<List<BlockView>> addBlock(Long doctorId, BlockedDateRequest r) {
        Doctor d = doctorId == null ? null
                : repository.findByIdForUpdate(doctorId).orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        LocalDate end = r.endDate() == null ? r.date() : r.endDate();
        if (end.isBefore(r.date())) {
            throw new BusinessException("endDate", "End date must not be before the start date");
        }
        if (ChronoUnit.DAYS.between(r.date(), end) >= MAX_BLOCK_RANGE_DAYS) {
            throw new BusinessException("endDate", "Block at most " + MAX_BLOCK_RANGE_DAYS + " days at a time");
        }
        if ((r.startTime() == null) != (r.endTime() == null)) {
            throw new BusinessException("endTime", "Provide both start and end time, or neither for a full-day block");
        }
        if (r.startTime() != null && !r.endTime().isAfter(r.startTime())) {
            throw new BusinessException("endTime", "End time must be after start time");
        }
        List<DoctorBlockedDate> created = new ArrayList<>();
        int conflicts = 0;
        for (LocalDate date = r.date(); !date.isAfter(end); date = date.plusDays(1)) {
            DoctorBlockedDate b = new DoctorBlockedDate();
            b.setDoctor(d);
            b.setBlockDate(date);
            b.setStartTime(r.startTime());
            b.setEndTime(r.endTime());
            b.setReason(ValidationPatterns.trimToNull(r.reason()));
            created.add(blockedRepository.save(b));
            final LocalDate day = date;
            List<Appointment> affected = d == null
                    ? appointmentRepository.findAll(com.clinic.repository.AppointmentSpecifications.onOrAfter(day)
                    .and(com.clinic.repository.AppointmentSpecifications.onOrBefore(day)))
                    : appointmentRepository.findActiveForDoctorOnDate(d.getId(), day);
            conflicts += (int) affected.stream()
                    .filter(a -> a.getStatus().occupiesSlot())
                    .filter(a -> b.overlaps(a.getAppointmentTime(), a.getEndTime()))
                    .count();
        }
        audit.record("CREATE", d == null ? "ClinicHoliday" : "DoctorBlockedDate", d == null ? null : d.getId(),
                (d == null ? "Clinic-wide" : d.getFullName()) + " blocked " + r.date() + (end.equals(r.date()) ? "" : " to " + end)
                        + (r.startTime() == null ? " (full day)" : " " + r.startTime() + "-" + r.endTime()));
        return new ChangeResult<>(created.stream().map(mapper::block).toList(), conflicts);
    }

    @Transactional
    public void deleteBlock(Long doctorId, Long blockId) {
        DoctorBlockedDate b = blockedRepository.findById(blockId)
                .filter(x -> doctorId == null ? x.getDoctor() == null : x.getDoctor() != null && x.getDoctor().getId().equals(doctorId))
                .orElseThrow(() -> new ResourceNotFoundException("Blocked date", blockId));
        blockedRepository.delete(b);
        audit.record("DELETE", doctorId == null ? "ClinicHoliday" : "DoctorBlockedDate", blockId, "Unblocked " + b.getBlockDate());
    }

    @Transactional(readOnly = true)
    public List<BlockView> upcomingClinicHolidays() {
        return blockedRepository.findUpcomingClinicWide(scheduleEngine.today()).stream().map(mapper::block).toList();
    }

    // ------------------------------------------------------------------ helpers

    Doctor load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
    }

    Doctor loadActive(Long id) {
        return repository.findById(id).filter(Doctor::isActive).orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
    }

    private ScheduleResponse scheduleResponse(Doctor d) {
        return new ScheduleResponse(d.getId(), mapper.weekly(d), mapper.specialHours(d),
                blockedRepository.findUpcomingForDoctor(d.getId(), scheduleEngine.today()).stream().map(mapper::block).toList());
    }

    /** Upcoming appointments of this doctor that no longer fall inside the doctor's hours (for an admin warning). */
    /**
     * Upcoming pending/confirmed appointments that no longer fit the doctor's hours (outside the shifts, or in the gap
     * between the morning and evening shift). One query for all of them; the hours are resolved in memory.
     */
    private int countUpcomingOutsideAvailability(Doctor d) {
        LocalDate today = scheduleEngine.today();
        int count = 0;
        for (Appointment a : appointmentRepository.findUpcomingForDoctor(d.getId(), today)) {
            Optional<WorkingHours> hours = scheduleEngine.hoursFor(d, a.getAppointmentDate());
            if (hours.isEmpty() || !fitsHours(hours.get(), a.getAppointmentTime(), a.getEndTime())) {
                count++;
            }
        }
        return count;
    }

    private static boolean fitsHours(WorkingHours h, LocalTime start, LocalTime end) {
        if (start.isBefore(h.getStartTime()) || end.isAfter(h.getEndTime())) {
            return false;
        }
        boolean hasBreak = h.getBreakStartTime() != null && h.getBreakEndTime() != null;
        return !hasBreak || !start.isBefore(h.getBreakEndTime()) || !end.isAfter(h.getBreakStartTime());
    }

}
