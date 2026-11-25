package com.clinic.repository;

import com.clinic.entity.DoctorBlockedDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DoctorBlockedDateRepository extends JpaRepository<DoctorBlockedDate, Long> {

    /** Blocks that apply to this doctor on this date: the doctor's own plus clinic-wide holidays. */
    @Query("""
            select b from DoctorBlockedDate b
            where b.blockDate = :date and (b.doctor.id = :doctorId or b.doctor is null)
            """)
    List<DoctorBlockedDate> findApplicable(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    @Query("""
            select b from DoctorBlockedDate b
            where b.blockDate between :from and :to and (b.doctor.id = :doctorId or b.doctor is null)
            order by b.blockDate, b.startTime
            """)
    List<DoctorBlockedDate> findApplicableInRange(@Param("doctorId") Long doctorId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    @Query("""
            select b from DoctorBlockedDate b left join fetch b.doctor
            where b.blockDate between :from and :to
            order by b.blockDate, b.startTime
            """)
    List<DoctorBlockedDate> findAllInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select b from DoctorBlockedDate b where b.doctor.id = :doctorId and b.blockDate >= :from order by b.blockDate, b.startTime")
    List<DoctorBlockedDate> findUpcomingForDoctor(@Param("doctorId") Long doctorId, @Param("from") LocalDate from);

    @Query("select b from DoctorBlockedDate b where b.doctor is null and b.blockDate >= :from order by b.blockDate")
    List<DoctorBlockedDate> findUpcomingClinicWide(@Param("from") LocalDate from);
}
