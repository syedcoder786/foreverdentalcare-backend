package com.clinic.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Read models for doctor calendars and availability. */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public enum SlotStatus { AVAILABLE, BOOKED, BLOCKED, PAST }

    /** Public view of one bookable slot. */
    public record SlotView(LocalTime time, LocalTime endTime, boolean available) {
    }

    /** Public availability for one doctor on one date. */
    public record DayAvailability(
            Long doctorId,
            LocalDate date,
            boolean open,
            String reason,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime,
            Integer slotDurationMinutes,
            Integer appointmentMinutes,
            List<SlotView> slots) {
    }

    /** Admin view of a slot, including why it is not available. */
    public record AdminSlot(LocalTime time, LocalTime endTime, SlotStatus status, Long appointmentId) {
    }

    public record BlockView(Long id, Long doctorId, String doctorName, boolean clinicWide, LocalDate date,
                            LocalTime startTime, LocalTime endTime, String reason) {
    }

    /** Admin daily calendar for one doctor. */
    public record AdminDaySchedule(
            DoctorRef doctor,
            LocalDate date,
            boolean open,
            String reason,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime,
            Integer slotDurationMinutes,
            List<AdminSlot> slots,
            List<AppointmentResponse> appointments,
            List<BlockView> blocks) {
    }

    /** Appointments + blocks for a date range (week / month views, one or all doctors). */
    public record CalendarRange(LocalDate from, LocalDate to, List<DoctorRef> doctors,
                                List<AppointmentResponse> appointments, List<BlockView> blocks,
                                List<WeeklyDay> weeklyHours) {
    }

    /** Weekly hours per doctor so calendars can shade working time. */
    public record WeeklyDay(Long doctorId, DayOfWeek dayOfWeek, boolean active, LocalTime startTime, LocalTime endTime,
                            LocalTime breakStartTime, LocalTime breakEndTime, Integer slotDurationMinutes) {
    }

    public record DoctorRef(Long id, String fullName, String slug, String professionalTitle) {
    }
}
