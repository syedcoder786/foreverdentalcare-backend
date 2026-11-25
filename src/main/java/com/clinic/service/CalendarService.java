package com.clinic.service;

import com.clinic.dto.CatalogDtos.DoctorAppointments;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.ScheduleDtos.*;
import com.clinic.entity.Appointment;
import com.clinic.entity.DentalService;
import com.clinic.entity.Doctor;
import com.clinic.entity.WorkingHours;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.mapper.DoctorMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.AppointmentSpecifications;
import com.clinic.repository.DentalServiceRepository;
import com.clinic.repository.DoctorBlockedDateRepository;
import com.clinic.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Read side of doctor calendars: public availability and admin daily / range calendars. */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final int MAX_RANGE_DAYS = 62;

    private final DoctorRepository doctorRepository;
    private final DentalServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorBlockedDateRepository blockedRepository;
    private final ScheduleEngine engine;
    private final AppointmentMapper appointmentMapper;
    private final DoctorMapper doctorMapper;

    /** Public: every slot of the doctor's day with an available flag (booked/blocked/past slots are not bookable). */
    @Transactional(readOnly = true)
    public DayAvailability availability(Long doctorId, LocalDate date, Long serviceId) {
        Doctor doctor = doctorRepository.findById(doctorId).filter(Doctor::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        int minutes = serviceMinutes(doctor, serviceId);
        ScheduleEngine.DayPlan plan = engine.plan(doctor, date, minutes, null);
        Optional<WorkingHours> h = plan.hours();
        List<SlotView> slots = plan.open()
                ? plan.candidates().stream().map(c -> new SlotView(c.start(), c.end(), c.available())).toList()
                : List.of();
        return new DayAvailability(doctorId, date, plan.open(), plan.closedReason(),
                h.map(WorkingHours::getStartTime).orElse(null), h.map(WorkingHours::getEndTime).orElse(null),
                h.map(WorkingHours::getBreakStartTime).orElse(null), h.map(WorkingHours::getBreakEndTime).orElse(null),
                h.map(WorkingHours::getSlotDurationMinutes).orElse(null), plan.appointmentMinutes(), slots);
    }

    /** Public: only the bookable slots. */
    @Transactional(readOnly = true)
    public List<SlotView> availableSlots(Long doctorId, LocalDate date, Long serviceId) {
        return availability(doctorId, date, serviceId).slots().stream().filter(SlotView::available).toList();
    }

    /** Admin: one doctor's day - every slot with its status, the appointments and blocked times. */
    @Transactional(readOnly = true)
    public AdminDaySchedule adminDay(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        Optional<WorkingHours> h = engine.hoursFor(doctor, date);
        int slot = h.map(WorkingHours::getSlotDurationMinutes).orElse(30);
        ScheduleEngine.DayPlan plan = engine.plan(doctor, date, slot, null);
        List<AdminSlot> slots = plan.candidates().stream()
                .map(c -> new AdminSlot(c.start(), c.end(), SlotStatus.valueOf(c.reason().name()), c.appointmentId()))
                .toList();
        List<AppointmentResponse> appointments = appointmentRepository.findAll(
                        AppointmentSpecifications.hasDoctor(doctorId)
                                .and(AppointmentSpecifications.onOrAfter(date))
                                .and(AppointmentSpecifications.onOrBefore(date)),
                        Sort.by("appointmentTime"))
                .stream().map(appointmentMapper::toResponse).toList();
        return new AdminDaySchedule(doctorMapper.ref(doctor), date, plan.open(), plan.closedReason(),
                h.map(WorkingHours::getStartTime).orElse(null), h.map(WorkingHours::getEndTime).orElse(null),
                h.map(WorkingHours::getBreakStartTime).orElse(null), h.map(WorkingHours::getBreakEndTime).orElse(null),
                h.map(WorkingHours::getSlotDurationMinutes).orElse(null), slots, appointments,
                plan.blocks().stream().map(doctorMapper::block).toList());
    }

    /** Admin: appointments, blocks and weekly hours in a date range for one doctor (doctorId) or all doctors. */
    @Transactional(readOnly = true)
    public CalendarRange range(LocalDate from, LocalDate to, Long doctorId) {
        if (to.isBefore(from)) {
            throw new BusinessException("to", "End date must not be before start date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException("to", "Choose a range of at most " + MAX_RANGE_DAYS + " days");
        }
        List<Doctor> doctors = doctorId == null
                ? doctorRepository.findAllByOrderByDisplayOrderAscFullNameAsc()
                : List.of(doctorRepository.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId)));
        Specification<Appointment> spec = Specification.allOf(
                AppointmentSpecifications.hasDoctor(doctorId),
                AppointmentSpecifications.onOrAfter(from),
                AppointmentSpecifications.onOrBefore(to));
        List<AppointmentResponse> appointments = appointmentRepository
                .findAll(spec, Sort.by("appointmentDate", "appointmentTime"))
                .stream().map(appointmentMapper::toResponse).toList();
        List<BlockView> blocks = (doctorId == null
                ? blockedRepository.findAllInRange(from, to)
                : blockedRepository.findApplicableInRange(doctorId, from, to))
                .stream().map(doctorMapper::block).toList();
        return new CalendarRange(from, to, doctors.stream().map(doctorMapper::ref).toList(), appointments, blocks,
                doctors.stream().flatMap(d -> doctorMapper.weeklyDays(d).stream()).toList());
    }

    @Transactional(readOnly = true)
    public DoctorAppointments doctorAppointments(Long doctorId, LocalDate from, LocalDate to) {
        CalendarRange r = range(from, to, doctorId);
        return new DoctorAppointments(r.doctors().getFirst(), r.appointments());
    }

    private int serviceMinutes(Doctor doctor, Long serviceId) {
        if (serviceId == null) {
            return engine.hoursFor(doctor, engine.today()).map(WorkingHours::getSlotDurationMinutes).orElse(30);
        }
        DentalService s = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new BusinessException("serviceId", "Selected treatment does not exist"));
        if (!doctor.offers(s.getId())) {
            throw new BusinessException("serviceId", doctor.getFullName() + " does not offer " + s.getName());
        }
        return s.getEstimatedDuration();
    }
}
