package com.clinic.repository;

import com.clinic.entity.Appointment;
import com.clinic.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /**
     * True if the doctor already has a non-cancelled appointment intersecting [start, end) on that date.
     * Different doctors never conflict. Pass excludeId = -1 when not rescheduling.
     */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctor.id = :doctorId and a.appointmentDate = :date
              and a.status <> com.clinic.enums.AppointmentStatus.CANCELLED
              and a.appointmentTime < :end and a.endTime > :start
              and a.id <> :excludeId
            """)
    boolean existsOverlap(@Param("doctorId") Long doctorId,
                          @Param("date") LocalDate date,
                          @Param("start") LocalTime start,
                          @Param("end") LocalTime end,
                          @Param("excludeId") long excludeId);

    /** Slot-reserving (pending/confirmed/completed) appointments of one doctor on one date. */
    @Query("""
            select a from Appointment a
            where a.doctor.id = :doctorId and a.appointmentDate = :date
              and a.status <> com.clinic.enums.AppointmentStatus.CANCELLED
            order by a.appointmentTime
            """)
    List<Appointment> findActiveForDoctorOnDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    long countByStatus(AppointmentStatus status);

    long countByAppointmentDateAndStatusNot(LocalDate date, AppointmentStatus status);

    @EntityGraph(attributePaths = {"patient", "service", "doctor"})
    List<Appointment> findByAppointmentDateAndStatusNotOrderByAppointmentTimeAsc(LocalDate date, AppointmentStatus status);

    @EntityGraph(attributePaths = {"patient", "service", "doctor"})
    Optional<Appointment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"service", "doctor"})
    List<Appointment> findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(Long patientId);

    @Override
    @EntityGraph(attributePaths = {"patient", "service", "doctor"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"patient", "service", "doctor"})
    List<Appointment> findAll(Specification<Appointment> spec);

    boolean existsByPatientIdAndStatusInAndIdNot(Long patientId, Collection<AppointmentStatus> statuses, long excludeId);

    /** The patient's active appointment currently carrying the first-visit discount, if any. */
    Optional<Appointment> findFirstByPatientIdAndStatusInAndFirstVisitDiscountPercentIsNotNullAndIdNot(
            Long patientId, Collection<AppointmentStatus> statuses, long excludeId);

    /**
     * Earliest pending online booking of the patient that has a fee but no discount (receives it when the holder is
     * cancelled).
     */
    Optional<Appointment> findFirstByPatientIdAndStatusAndOnlineBookingTrueAndConsultationFeeGreaterThanAndFirstVisitDiscountPercentIsNullAndIdNotOrderByAppointmentDateAscAppointmentTimeAsc(
            Long patientId, AppointmentStatus status, BigDecimal minFee, long excludeId);

    boolean existsByServiceId(Long serviceId);

    boolean existsByDoctorId(Long doctorId);

    /** Rows of [doctorId, doctorName, status, count] for the dashboard's per-doctor breakdown. */
    @Query("""
            select a.doctor.id, a.doctor.fullName, a.status, count(a) from Appointment a
            group by a.doctor.id, a.doctor.fullName, a.status
            """)
    List<Object[]> countByDoctorAndStatus();

    @Query("select a.patient.id, count(a) from Appointment a where a.patient.id in :patientIds group by a.patient.id")
    List<Object[]> countByPatientIds(@Param("patientIds") List<Long> patientIds);
}
