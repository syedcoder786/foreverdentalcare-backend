package com.clinic.repository;

import com.clinic.entity.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    List<Testimonial> findAllByPublishedTrueAndConsentConfirmedTrueOrderByDisplayOrderAscIdAsc();

    List<Testimonial> findAllByOrderByDisplayOrderAscIdAsc();
}
