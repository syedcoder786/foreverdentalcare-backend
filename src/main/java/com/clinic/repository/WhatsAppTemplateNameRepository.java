package com.clinic.repository;

import com.clinic.entity.WhatsAppTemplateName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhatsAppTemplateNameRepository extends JpaRepository<WhatsAppTemplateName, Long> {

    List<WhatsAppTemplateName> findAllByOrderByNameAscLanguageAsc();

    boolean existsByNameAndLanguage(String name, String language);
}
