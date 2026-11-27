package com.clinic.service;

import com.clinic.dto.AdminDtos.AdminAppointmentRequest;
import com.clinic.dto.PatientDtos;
import com.clinic.dto.request.AppointmentRequest;
import com.clinic.dto.request.RescheduleRequest;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.BookingConfirmationResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.DentalService;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.enums.AppointmentStatus;
import com.clinic.exception.AuthenticationFailedException;
import com.clinic.exception.BusinessException;
import com.clinic.exception.InvalidStatusTransitionException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.*;
import com.clinic.util.ValidationPatterns;
import com.clinic.enums.TemplatePurpose;
import com.clinic.whatsapp.AppointmentMessageEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Appointment business logic. Every appointment belongs to exactly one doctor's calendar.
 * <p>
 * Conflict prevention (per doctor, never clinic-wide):
 * <ol>
 *   <li>The doctor's row is locked ({@code SELECT ... FOR UPDATE}) for the transaction, so concurrent bookings/reschedules
 *       for the <em>same</em> doctor run one after another; other doctors are unaffected.</li>
 *   <li>Under that lock, {@link ScheduleEngine#requireBookable} checks working hours, breaks, blocked time and
 *       time-range overlap with the doctor's non-cancelled appointments.</li>
 *   <li>Database backstops: a UNIQUE (doctor, date, start, slot_active) constraint everywhere, plus a PostgreSQL
 *       exclusion constraint rejecting any overlapping range for the same doctor. A violation becomes HTTP 409.</li>
 * </ol>
 * Cancelled appointments release their time (slot_active = NULL, excluded from overlap checks); rescheduling moves the
 * appointment's range, which releases the old time in the same transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DentalServiceRepository serviceRepository;
    private final DoctorRepository doctorRepository;
    private final ScheduleEngine scheduleEngine;
    private final AppointmentMapper mapper;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /** Public booking. New appointments always start as PENDING and must be confirmed by the clinic. */
    @Transactional
    public BookingConfirmationResponse requestAppointment(AppointmentRequest r) {
        Appointment saved = book(r.doctorId(), r.serviceId(), r.appointmentDate(), r.appointmentTime(),
                r.fullName(), r.phone(), r.email(), r.message(), AppointmentStatus.PENDING);
        log.info("Appointment request {} created for doctor {} on {} {}", saved.getId(), saved.getDoctor().getId(),
                saved.getAppointmentDate(), saved.getAppointmentTime());
        events.publishEvent(new AppointmentMessageEvent(saved.getId(), TemplatePurpose.APPOINTMENT_RECEIVED));
        return mapper.toConfirmation(saved);
    }

    /** Booking by a signed-in patient (verified mobile number). Name, phone and email come from their profile. */
    @Transactional
    public BookingConfirmationResponse requestForPatient(Long patientId, PatientDtos.PatientBookingRequest r) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new AuthenticationFailedException("Please sign in again."));
        Appointment saved = book(r.doctorId(), r.serviceId(), r.appointmentDate(), r.appointmentTime(),
                patient.getFullName(), patient.getPhone(), patient.getEmail(), r.message(), AppointmentStatus.PENDING);
        log.info("Appointment request {} created by patient {} for doctor {} on {} {}", saved.getId(), patientId,
                saved.getDoctor().getId(), saved.getAppointmentDate(), saved.getAppointmentTime());
        events.publishEvent(new AppointmentMessageEvent(saved.getId(), TemplatePurpose.APPOINTMENT_RECEIVED));
        return mapper.toConfirmation(saved);
    }

    /** The signed-in patient's appointments, newest first. */
    @Transactional(readOnly = true)
    public List<PatientDtos.PatientAppointmentResponse> listForPatient(Long patientId) {
        return appointmentRepository.findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(patientId).stream()
                .map(a -> new PatientDtos.PatientAppointmentResponse(
                        a.getId(), a.getStatus(), a.getService().getName(), a.getDoctor().getFullName(),
                        a.getDoctor().getSlug(), a.getAppointmentDate(), a.getAppointmentTime(), a.getEndTime(),
                        a.getMessage(), a.getCreatedAt()))
                .toList();
    }

    /** Appointment entered by clinic staff (e.g. phone booking). Same rules; may start as CONFIRMED. */
    @Transactional
    public AppointmentResponse createByAdmin(AdminAppointmentRequest r) {
        AppointmentStatus initial = r.initialStatus() == null ? AppointmentStatus.PENDING : r.initialStatus();
        if (initial != AppointmentStatus.PENDING && initial != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("initialStatus", "New appointments can only be PENDING or CONFIRMED");
        }
        Appointment saved = book(r.doctorId(), r.serviceId(), r.appointmentDate(), r.appointmentTime(),
                r.fullName(), r.phone(), r.email(), r.message(), initial);
        audit.record("CREATE", "Appointment", saved.getId(), "Manual booking with " + saved.getDoctor().getFullName()
                + " on " + saved.getAppointmentDate() + " " + saved.getAppointmentTime());
        events.publishEvent(new AppointmentMessageEvent(saved.getId(),
                initial == AppointmentStatus.CONFIRMED ? TemplatePurpose.APPOINTMENT_CONFIRMED : TemplatePurpose.APPOINTMENT_RECEIVED));
        return mapper.toResponse(saved);
    }

    private Appointment book(Long doctorId, Long serviceId, LocalDate date, LocalTime time, String fullName,
                             String phone, String email, String message, AppointmentStatus status) {
        DentalService service = serviceRepository.findById(serviceId)
                .filter(s -> s.isActive() && s.getSpecialty().isActive())
                .orElseThrow(() -> new BusinessException("serviceId", "Selected treatment is not available"));

        Long resolvedDoctorId = doctorId != null ? doctorId : singleDoctorFor(service);
        Doctor doctor = doctorRepository.findByIdForUpdate(resolvedDoctorId)
                .filter(Doctor::isActive)
                .orElseThrow(() -> new BusinessException("doctorId", "Selected doctor is not available"));
        if (!doctor.offers(service.getId())) {
            throw new BusinessException("serviceId", doctor.getFullName() + " does not offer " + service.getName());
        }

        LocalTime end = scheduleEngine.requireBookable(doctor, date, time, service.getEstimatedDuration(), null);

        Appointment appointment = new Appointment();
        appointment.setPatient(upsertPatient(fullName, phone, email));
        appointment.setService(service);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(date);
        appointment.setAppointmentTime(time);
        appointment.setEndTime(end);
        appointment.setMessage(ValidationPatterns.trimToNull(message));
        appointment.changeStatus(status);
        return saveGuardingSlot(appointment);
    }

    /** Backwards compatibility: a booking without doctorId is accepted only when exactly one active doctor offers it. */
    private static Long singleDoctorFor(DentalService service) {
        List<Doctor> doctors = service.getDoctors().stream().filter(Doctor::isActive).toList();
        if (doctors.size() != 1) {
            throw new BusinessException("doctorId", "Please choose a doctor for this treatment");
        }
        return doctors.getFirst().getId();
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(String search, AppointmentStatus status, Long doctorId, Long specialtyId,
                                                    LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("to", "End date must not be before start date");
        }
        Specification<Appointment> spec = Specification.allOf(
                AppointmentSpecifications.hasStatus(status),
                AppointmentSpecifications.hasDoctor(doctorId),
                AppointmentSpecifications.hasSpecialty(specialtyId),
                AppointmentSpecifications.onOrAfter(from),
                AppointmentSpecifications.onOrBefore(to),
                AppointmentSpecifications.matchesSearch(search));
        return PageResponse.from(appointmentRepository.findAll(spec, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, AppointmentStatus target) {
        Appointment appointment = load(id);
        AppointmentStatus current = appointment.getStatus();
        if (current == target) {
            return mapper.toResponse(appointment);
        }
        if (!current.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(current, target);
        }
        appointment.changeStatus(target);
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        audit.record("STATUS", "Appointment", id, current + " -> " + target);
        if (target == AppointmentStatus.CONFIRMED) {
            events.publishEvent(new AppointmentMessageEvent(id, TemplatePurpose.APPOINTMENT_CONFIRMED));
        } else if (target == AppointmentStatus.CANCELLED) {
            events.publishEvent(new AppointmentMessageEvent(id, TemplatePurpose.APPOINTMENT_CANCELLED));
        }
        return mapper.toResponse(saved);
    }

    /** Moves an appointment within the same doctor's calendar. The previous time is released in the same transaction. */
    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest request) {
        Appointment appointment = load(id);
        if (!appointment.getStatus().isReschedulable()) {
            throw new BusinessException("status",
                    "Only pending or confirmed appointments can be rescheduled (current: " + appointment.getStatus() + ")");
        }
        if (appointment.getAppointmentDate().equals(request.appointmentDate())
                && appointment.getAppointmentTime().equals(request.appointmentTime())) {
            throw new BusinessException("appointmentTime", "The new slot is the same as the current one");
        }
        Doctor doctor = doctorRepository.findByIdForUpdate(appointment.getDoctor().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", appointment.getDoctor().getId()));
        LocalTime end = scheduleEngine.requireBookable(doctor, request.appointmentDate(), request.appointmentTime(),
                appointment.getService().getEstimatedDuration(), appointment.getId());
        String previous = appointment.getAppointmentDate() + " " + appointment.getAppointmentTime();
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setAppointmentTime(request.appointmentTime());
        appointment.setEndTime(end);
        Appointment saved = saveGuardingSlot(appointment);
        audit.record("RESCHEDULE", "Appointment", id, previous + " -> " + saved.getAppointmentDate() + " " + saved.getAppointmentTime());
        events.publishEvent(new AppointmentMessageEvent(id, TemplatePurpose.APPOINTMENT_RESCHEDULED));
        return mapper.toResponse(saved);
    }

    private Appointment load(Long id) {
        return appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    /** Returning patients are identified by normalised phone number; their name/email are refreshed. */
    private Patient upsertPatient(String fullName, String rawPhone, String rawEmail) {
        String phone = ValidationPatterns.normalizePhone(rawPhone);
        Patient patient = patientRepository.findByPhone(phone).orElseGet(Patient::new);
        patient.setPhone(phone);
        patient.setFullName(fullName.trim());
        String email = ValidationPatterns.trimToNull(rawEmail);
        if (email != null) {
            patient.setEmail(email.toLowerCase());
        }
        return patientRepository.save(patient);
    }

    private Appointment saveGuardingSlot(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            // A database constraint (unique start / PostgreSQL overlap exclusion) rejected the write.
            throw new SlotUnavailableException();
        }
    }
}
