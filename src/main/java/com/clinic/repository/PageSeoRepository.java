package com.clinic.repository;

import com.clinic.entity.PageSeo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PageSeoRepository extends JpaRepository<PageSeo, Long> {

    Optional<PageSeo> findByPageKey(String pageKey);

    List<PageSeo> findAllByOrderByPageKeyAsc();
}
