package com.clinic.repository;

import com.clinic.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /**
     * Locks the doctor row (SELECT ... FOR UPDATE) for the rest of the transaction. Booking/rescheduling for the same
     * doctor is therefore serialised, which makes the overlap check race-free. Other doctors are not blocked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"specialties"})
    List<Doctor> findAllByActiveTrueOrderByDisplayOrderAscFullNameAsc();

    @EntityGraph(attributePaths = {"specialties"})
    List<Doctor> findAllByOrderByDisplayOrderAscFullNameAsc();

    Optional<Doctor> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    long countByActiveTrue();

    @Query("select count(d) > 0 from Doctor d join d.specialties s where s.id = :specialtyId")
    boolean existsBySpecialtyId(@Param("specialtyId") Long specialtyId);
}
