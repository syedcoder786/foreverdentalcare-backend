package com.clinic.repository;

import com.clinic.entity.WhatsAppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppSettingsRepository extends JpaRepository<WhatsAppSettings, Long> {
}
