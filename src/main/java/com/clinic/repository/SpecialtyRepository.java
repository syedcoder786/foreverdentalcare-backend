package com.clinic.repository;

import com.clinic.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    List<Specialty> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Specialty> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<Specialty> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    long countByActiveTrue();
}
