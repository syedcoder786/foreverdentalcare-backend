package com.clinic.repository;

import com.clinic.entity.DentalService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DentalServiceRepository extends JpaRepository<DentalService, Long> {

    @EntityGraph(attributePaths = {"specialty"})
    List<DentalService> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    @EntityGraph(attributePaths = {"specialty"})
    List<DentalService> findAllByOrderByDisplayOrderAscNameAsc();

    @EntityGraph(attributePaths = {"specialty"})
    Optional<DentalService> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsBySpecialtyId(Long specialtyId);

    long countByActiveTrue();
}
