package com.clinic.repository;

import com.clinic.entity.WebsiteSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebsiteSectionRepository extends JpaRepository<WebsiteSection, Long> {

    List<WebsiteSection> findAllByPageKeyAndActiveTrueOrderByDisplayOrderAscIdAsc(String pageKey);

    List<WebsiteSection> findAllByPageKeyOrderByDisplayOrderAscIdAsc(String pageKey);

    boolean existsByPageKeyAndSectionKey(String pageKey, String sectionKey);
}
