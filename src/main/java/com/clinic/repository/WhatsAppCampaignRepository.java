package com.clinic.repository;

import com.clinic.entity.WhatsAppCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhatsAppCampaignRepository extends JpaRepository<WhatsAppCampaign, Long> {

    List<WhatsAppCampaign> findTop20ByOrderByCreatedAtDesc();
}
