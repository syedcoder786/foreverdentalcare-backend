package com.clinic.repository;

import com.clinic.entity.WhatsAppTemplate;
import com.clinic.enums.TemplatePurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplate, Long> {

    Optional<WhatsAppTemplate> findFirstByPurposeOrderByIdAsc(TemplatePurpose purpose);

    boolean existsByPurpose(TemplatePurpose purpose);

    boolean existsByTemplateNameAndTemplateLanguage(String templateName, String templateLanguage);

    List<WhatsAppTemplate> findAllByOrderByIdAsc();
}
