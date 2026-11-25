package com.clinic.repository;

import com.clinic.entity.Appointment;
import com.clinic.entity.DentalService;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.enums.AppointmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the per-doctor database guarantees and query methods against the real Flyway schema. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppointmentRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 7);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DentalServiceRepository serviceRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Patient patient;
    private DentalService service;
    private Doctor dentist;
    private Doctor other;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setFullName("Ravi Kumar");
        patient.setPhone("+919000000001");
        patient = patientRepository.saveAndFlush(patient);
        service = serviceRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().getFirst();
        dentist = doctorRepository.findBySlug("dentist").orElseThrow();
        other = new Doctor();
        other.setFullName("Second Doctor");
        other.setSlug("repo-test-second");
        other.setProfessionalTitle("Physician");
        other = doctorRepository.saveAndFlush(other);
    }

    private Appointment appointment(Doctor doctor, LocalTime start, LocalTime end, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setService(service);
        a.setDoctor(doctor);
        a.setAppointmentDate(DAY);
        a.setAppointmentTime(start);
        a.setEndTime(end);
        a.changeStatus(status);
        return a;
    }

    @Test
    void seedDataIsPresent() {
        assertThat(serviceRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc()).hasSize(8);
        assertThat(dentist.getAvailability()).hasSize(7);
        assertThat(dentist.getServices()).hasSize(8);
        assertThat(dentist.isCredentialsVerified()).isFalse();
    }

    @Test
    void sameDoctorCannotHaveTwoActiveAppointmentsStartingTogether() {
        appointmentRepository.saveAndFlush(appointment(dentist, TEN, LocalTime.of(10, 30), AppointmentStatus.PENDING));
        assertThatThrownBy(() -> appointmentRepository.saveAndFlush(
                appointment(dentist, TEN, LocalTime.of(10, 30), AppointmentStatus.CONFIRMED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentDoctorsMayBookTheSameTime() {
        appointmentRepository.saveAndFlush(appointment(dentist, TEN, LocalTime.of(10, 30), AppointmentStatus.PENDING));
        Appointment second = appointmentRepository.saveAndFlush(appointment(other, TEN, LocalTime.of(10, 30), AppointmentStatus.PENDING));
        assertThat(second.getId()).isNotNull();
    }

    @Test
    void overlapQueryIsScopedToOneDoctorAndIgnoresCancelled() {
        Appointment a = appointmentRepository.saveAndFlush(appointment(dentist, TEN, LocalTime.of(11, 0), AppointmentStatus.CONFIRMED));

        assertThat(appointmentRepository.existsOverlap(dentist.getId(), DAY, LocalTime.of(10, 30), LocalTime.of(11, 0), -1L)).isTrue();
        assertThat(appointmentRepository.existsOverlap(dentist.getId(), DAY, LocalTime.of(11, 0), LocalTime.of(11, 30), -1L)).isFalse();
        assertThat(appointmentRepository.existsOverlap(other.getId(), DAY, LocalTime.of(10, 30), LocalTime.of(11, 0), -1L)).isFalse();
        assertThat(appointmentRepository.existsOverlap(dentist.getId(), DAY, LocalTime.of(10, 30), LocalTime.of(11, 0), a.getId())).isFalse();

        a.changeStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.saveAndFlush(a);
        assertThat(appointmentRepository.existsOverlap(dentist.getId(), DAY, LocalTime.of(10, 30), LocalTime.of(11, 0), -1L)).isFalse();
        assertThat(appointmentRepository.findActiveForDoctorOnDate(dentist.getId(), DAY)).isEmpty();
    }

    @Test
    void cancelledAppointmentsDoNotBlockTheSlot() {
        Appointment first = appointmentRepository.saveAndFlush(appointment(dentist, TEN, LocalTime.of(10, 30), AppointmentStatus.PENDING));
        first.changeStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.saveAndFlush(first);
        Appointment rebooked = appointmentRepository.saveAndFlush(appointment(dentist, TEN, LocalTime.of(10, 30), AppointmentStatus.PENDING));
        assertThat(rebooked.getId()).isNotNull();
    }

    @Test
    void searchSpecificationMatchesNameAndPhone() {
        appointmentRepository.saveAndFlush(appointment(dentist, TEN, LocalTime.of(10, 30), AppointmentStatus.PENDING));

        var byName = appointmentRepository.findAll(Specification.allOf(AppointmentSpecifications.matchesSearch("ravi")), PageRequest.of(0, 10));
        var byPhone = appointmentRepository.findAll(Specification.allOf(AppointmentSpecifications.matchesSearch("90000 00001")), PageRequest.of(0, 10));
        var byDoctor = appointmentRepository.findAll(Specification.allOf(AppointmentSpecifications.hasDoctor(other.getId())), PageRequest.of(0, 10));

        assertThat(byName.getTotalElements()).isEqualTo(1);
        assertThat(byPhone.getTotalElements()).isEqualTo(1);
        assertThat(byDoctor.getTotalElements()).isZero();
    }

    @Test
    void databaseRejectsActiveAppointmentWithoutSlotFlag() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO appointments (patient_id, service_id, doctor_id, appointment_date, appointment_time, end_time,
                                          status, slot_active, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, patient.getId(), service.getId(), dentist.getId(), DAY, TEN, LocalTime.of(10, 30)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsEndBeforeStart() {
        assertThatThrownBy(() -> appointmentRepository.saveAndFlush(
                appointment(dentist, TEN, LocalTime.of(9, 30), AppointmentStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
