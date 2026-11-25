package com.clinic.service;

import com.clinic.entity.*;
import com.clinic.enums.AppointmentStatus;
import com.clinic.exception.BusinessException;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorBlockedDateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleEngineTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** Wednesday 2026-12-02 16:10 clinic time. */
    private static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 12, 2, 16, 10).atZone(ZONE).toInstant(), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 2);
    private static final LocalDate THURSDAY = LocalDate.of(2026, 12, 3);

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private DoctorBlockedDateRepository blockedRepository;
    @Mock
    private ClinicSettingsService settingsService;

    private ScheduleEngine engine;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        engine = new ScheduleEngine(appointmentRepository, blockedRepository, settingsService, CLOCK);
        ClinicSettings settings = new ClinicSettings();
        settings.setBookingWindowDays(30);
        when(settingsService.getSettings()).thenReturn(settings);
        when(appointmentRepository.findActiveForDoctorOnDate(anyLong(), any())).thenReturn(List.of());
        when(blockedRepository.findApplicable(anyLong(), any())).thenReturn(List.of());

        doctor = new Doctor();
        doctor.setId(7L);
        doctor.setActive(true);
        // Evening practitioner: Mon-Sat 16:00-20:00, break 18:00-18:30, 30-minute slots.
        for (DayOfWeek day : DayOfWeek.values()) {
            DoctorAvailability a = new DoctorAvailability();
            a.setDoctor(doctor);
            a.setDayOfWeek(day);
            a.setStartTime(LocalTime.of(16, 0));
            a.setEndTime(LocalTime.of(20, 0));
            a.setBreakStartTime(LocalTime.of(18, 0));
            a.setBreakEndTime(LocalTime.of(18, 30));
            a.setSlotDurationMinutes(30);
            a.setActive(day != DayOfWeek.SUNDAY);
            doctor.getAvailability().add(a);
        }
    }

    private List<LocalTime> available(LocalDate date, int minutes) {
        return engine.plan(doctor, date, minutes, null).candidates().stream()
                .filter(ScheduleEngine.Candidate::available).map(ScheduleEngine.Candidate::start).toList();
    }

    @Test
    void slotsSkipTheBreak() {
        assertThat(available(THURSDAY, 30)).containsExactly(
                LocalTime.of(16, 0), LocalTime.of(16, 30), LocalTime.of(17, 0), LocalTime.of(17, 30),
                LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30));
    }

    @Test
    void longerServicesOccupyWholeSlotsAndMustNotCrossTheBreak() {
        assertThat(ScheduleEngine.occupiedMinutes(45, 30)).isEqualTo(60);
        assertThat(ScheduleEngine.occupiedMinutes(10, 30)).isEqualTo(30);
        // 60-min appointment: 17:30 would run into the break, 19:30 past closing.
        assertThat(available(THURSDAY, 45)).containsExactly(
                LocalTime.of(16, 0), LocalTime.of(16, 30), LocalTime.of(17, 0), LocalTime.of(18, 30), LocalTime.of(19, 0));
    }

    @Test
    void pastSlotsTodayAreUnavailable() {
        assertThat(available(TODAY, 30)).startsWith(LocalTime.of(16, 30)).doesNotContain(LocalTime.of(16, 0));
    }

    @Test
    void weeklyOffDayAndInactiveDoctorAreClosed() {
        assertThat(engine.plan(doctor, LocalDate.of(2026, 12, 6), 30, null).open()).isFalse(); // Sunday
        doctor.setActive(false);
        assertThat(engine.plan(doctor, THURSDAY, 30, null).open()).isFalse();
    }

    @Test
    void overlappingAppointmentsMarkSlotsBooked() {
        Appointment existing = new Appointment();
        existing.setId(99L);
        existing.setAppointmentTime(LocalTime.of(16, 30));
        existing.setEndTime(LocalTime.of(17, 30));
        existing.changeStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findActiveForDoctorOnDate(7L, THURSDAY)).thenReturn(List.of(existing));

        assertThat(available(THURSDAY, 30)).doesNotContain(LocalTime.of(16, 30), LocalTime.of(17, 0)).contains(LocalTime.of(16, 0));
        assertThatThrownBy(() -> engine.requireBookable(doctor, THURSDAY, LocalTime.of(17, 0), 30, null))
                .isInstanceOf(SlotUnavailableException.class);
        // Rescheduling the same appointment ignores its own current reservation.
        assertThat(engine.requireBookable(doctor, THURSDAY, LocalTime.of(17, 0), 30, 99L)).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void blockedTimesAreUnavailable() {
        DoctorBlockedDate partial = new DoctorBlockedDate();
        partial.setBlockDate(THURSDAY);
        partial.setStartTime(LocalTime.of(19, 0));
        partial.setEndTime(LocalTime.of(20, 0));
        when(blockedRepository.findApplicable(7L, THURSDAY)).thenReturn(List.of(partial));
        assertThat(available(THURSDAY, 30)).doesNotContain(LocalTime.of(19, 0), LocalTime.of(19, 30)).contains(LocalTime.of(18, 30));

        DoctorBlockedDate fullDay = new DoctorBlockedDate();
        fullDay.setBlockDate(THURSDAY);
        fullDay.setReason("Leave");
        when(blockedRepository.findApplicable(7L, THURSDAY)).thenReturn(List.of(fullDay));
        ScheduleEngine.DayPlan plan = engine.plan(doctor, THURSDAY, 30, null);
        assertThat(plan.open()).isFalse();
        assertThat(plan.closedReason()).isEqualTo("Leave");
    }

    @Test
    void specialHoursOverrideWeeklySchedule() {
        DoctorSpecialHours special = new DoctorSpecialHours();
        special.setDoctor(doctor);
        special.setSpecialDate(THURSDAY);
        special.setStartTime(LocalTime.of(9, 0));
        special.setEndTime(LocalTime.of(10, 0));
        special.setSlotDurationMinutes(20);
        doctor.getSpecialHours().add(special);
        assertThat(available(THURSDAY, 15)).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 20), LocalTime.of(9, 40));
    }

    @Test
    void offGridAndOutOfHoursTimesAreRejected() {
        assertThatThrownBy(() -> engine.requireBookable(doctor, THURSDAY, LocalTime.of(16, 15), 30, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> engine.requireBookable(doctor, THURSDAY, LocalTime.of(10, 0), 30, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> engine.requireBookable(doctor, THURSDAY, LocalTime.of(18, 0), 30, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> engine.requireBookable(doctor, LocalDate.of(2027, 2, 1), LocalTime.of(16, 0), 30, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("in advance");
    }
}
