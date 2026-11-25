package com.clinic.service;

import com.clinic.entity.*;
import com.clinic.exception.BusinessException;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorBlockedDateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Per-doctor scheduling rules. Every calculation is scoped to ONE doctor: another doctor's hours, blocks or
 * appointments never affect it. "Now"/"today" use the clinic-timezone {@link Clock}.
 *
 * <ul>
 *   <li>Hours for a date = the doctor's special hours for that date, else that weekday's schedule (if active).</li>
 *   <li>Slots start every {@code slotDuration} minutes; an appointment occupies the service duration rounded up to whole
 *       slots, and must fit inside working hours without touching the break.</li>
 *   <li>A slot is unavailable if it is in the past, overlaps a blocked time (doctor or clinic-wide), or overlaps one of
 *       this doctor's non-cancelled appointments.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScheduleEngine {

    private final AppointmentRepository appointmentRepository;
    private final DoctorBlockedDateRepository blockedDateRepository;
    private final ClinicSettingsService settingsService;
    private final Clock clock;

    /** A candidate slot [start, end) plus why it is (un)available. */
    public record Candidate(LocalTime start, LocalTime end, Reason reason, Long appointmentId) {
        public boolean available() {
            return reason == Reason.AVAILABLE;
        }
    }

    public enum Reason { AVAILABLE, BOOKED, BLOCKED, PAST }

    /** Result of evaluating one doctor-day. {@code hours} is empty when the doctor does not work that day. */
    public record DayPlan(LocalDate date, Optional<WorkingHours> hours, String closedReason, int appointmentMinutes,
                          List<Candidate> candidates, List<Appointment> appointments, List<DoctorBlockedDate> blocks) {
        public boolean open() {
            return hours.isPresent() && closedReason == null;
        }
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Doctor's working hours on a date, honouring date-specific special hours first. */
    public Optional<WorkingHours> hoursFor(Doctor doctor, LocalDate date) {
        for (DoctorSpecialHours special : doctor.getSpecialHours()) {
            if (special.getSpecialDate().equals(date)) {
                return Optional.of(special);
            }
        }
        return doctor.getAvailability().stream()
                .filter(a -> a.isActive() && a.getDayOfWeek() == date.getDayOfWeek())
                .<WorkingHours>map(a -> a)
                .findFirst();
    }

    /** Minutes an appointment occupies: the service duration rounded up to whole slots (at least one slot). */
    public static int occupiedMinutes(int serviceMinutes, int slotMinutes) {
        int slots = Math.max(1, (int) Math.ceil(serviceMinutes / (double) slotMinutes));
        return slots * slotMinutes;
    }

    /** All slot start times that fit the hours for an appointment of `lengthMinutes`, skipping the break. */
    public static List<LocalTime[]> slotGrid(WorkingHours h, int lengthMinutes) {
        List<LocalTime[]> grid = new ArrayList<>();
        int step = h.getSlotDurationMinutes();
        int open = minutes(h.getStartTime());
        int close = minutes(h.getEndTime());
        boolean hasBreak = h.getBreakStartTime() != null && h.getBreakEndTime() != null;
        int bs = hasBreak ? minutes(h.getBreakStartTime()) : -1;
        int be = hasBreak ? minutes(h.getBreakEndTime()) : -1;
        for (int start = open; start + lengthMinutes <= close; start += step) {
            int end = start + lengthMinutes;
            if (hasBreak && start < be && end > bs) {
                continue;
            }
            grid.add(new LocalTime[]{toTime(start), toTime(end)});
        }
        return grid;
    }

    /**
     * Evaluates a doctor's day for an appointment of the given service duration.
     *
     * @param excludeAppointmentId appointment to ignore (the one being rescheduled), or null
     */
    public DayPlan plan(Doctor doctor, LocalDate date, int serviceMinutes, Long excludeAppointmentId) {
        ClinicSettings settings = settingsService.getSettings();
        LocalDateTime now = LocalDateTime.now(clock);
        List<DoctorBlockedDate> blocks = blockedDateRepository.findApplicable(doctor.getId(), date);
        List<Appointment> appointments = appointmentRepository.findActiveForDoctorOnDate(doctor.getId(), date).stream()
                .filter(a -> excludeAppointmentId == null || !a.getId().equals(excludeAppointmentId))
                .toList();

        Optional<WorkingHours> hours = hoursFor(doctor, date);
        String closed = null;
        if (!doctor.isActive()) {
            closed = "This doctor is not currently taking appointments";
        } else if (date.isBefore(now.toLocalDate())) {
            closed = "Date is in the past";
        } else if (date.isAfter(now.toLocalDate().plusDays(settings.getBookingWindowDays()))) {
            closed = "Bookings open " + settings.getBookingWindowDays() + " days in advance";
        } else if (hours.isEmpty()) {
            closed = "Not available on this day";
        } else if (blocks.stream().anyMatch(DoctorBlockedDate::isFullDay)) {
            closed = blocks.stream().filter(DoctorBlockedDate::isFullDay).map(DoctorBlockedDate::getReason)
                    .filter(r -> r != null && !r.isBlank()).findFirst().orElse("Not available on this date");
        }
        if (hours.isEmpty()) {
            return new DayPlan(date, hours, closed, 0, List.of(), appointments, blocks);
        }

        WorkingHours h = hours.get();
        int length = occupiedMinutes(serviceMinutes, h.getSlotDurationMinutes());
        List<Candidate> candidates = new ArrayList<>();
        for (LocalTime[] slot : slotGrid(h, length)) {
            LocalTime start = slot[0];
            LocalTime end = slot[1];
            Reason reason;
            Long apptId = null;
            Optional<Appointment> clash = appointments.stream()
                    .filter(a -> start.isBefore(a.getEndTime()) && end.isAfter(a.getAppointmentTime()))
                    .findFirst();
            if (clash.isPresent()) {
                reason = Reason.BOOKED;
                apptId = clash.get().getId();
            } else if (blocks.stream().anyMatch(b -> b.overlaps(start, end))) {
                reason = Reason.BLOCKED;
            } else if (!date.atTime(start).isAfter(now)) {
                reason = Reason.PAST;
            } else {
                reason = Reason.AVAILABLE;
            }
            candidates.add(new Candidate(start, end, closed == null ? reason : Reason.BLOCKED, apptId));
        }
        return new DayPlan(date, hours, closed, length, candidates, appointments, blocks);
    }

    /**
     * Validates that `start` is a bookable slot for this doctor and returns the appointment's end time.
     * Must be called inside a transaction holding the doctor's row lock (see AppointmentService) so the overlap
     * check cannot race with a concurrent booking for the same doctor.
     */
    public LocalTime requireBookable(Doctor doctor, LocalDate date, LocalTime start, int serviceMinutes, Long excludeId) {
        if (start.getSecond() != 0 || start.getNano() != 0) {
            throw new BusinessException("appointmentTime", "Please choose one of the available time slots");
        }
        DayPlan plan = plan(doctor, date, serviceMinutes, excludeId);
        if (!plan.open()) {
            String field = plan.hours().isEmpty() || plan.closedReason() != null ? "appointmentDate" : "appointmentTime";
            throw new BusinessException(field, plan.closedReason() != null ? plan.closedReason() : "Not available on this day");
        }
        Candidate slot = plan.candidates().stream().filter(c -> c.start().equals(start)).findFirst()
                .orElseThrow(() -> new BusinessException("appointmentTime",
                        "Please choose one of the doctor's available time slots"));
        return switch (slot.reason()) {
            case AVAILABLE -> slot.end();
            case BOOKED -> throw new com.clinic.exception.SlotUnavailableException();
            case BLOCKED -> throw new BusinessException("appointmentTime", "The doctor is not available at this time");
            case PAST -> throw new BusinessException("appointmentTime", "The selected time has already passed");
        };
    }

    private static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private static LocalTime toTime(int minutes) {
        return minutes >= 24 * 60 ? LocalTime.MAX.withSecond(0).withNano(0) : LocalTime.of(minutes / 60, minutes % 60);
    }
}
