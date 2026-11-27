package com.clinic.service;

import com.clinic.dto.WhatsAppTemplateDtos.AudienceResponse;
import com.clinic.dto.WhatsAppTemplateDtos.CampaignResponse;
import com.clinic.entity.WhatsAppCampaign;
import com.clinic.entity.WhatsAppSettings;
import com.clinic.entity.WhatsAppTemplate;
import com.clinic.enums.TemplatePurpose;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.WhatsAppCampaignRepository;
import com.clinic.repository.WhatsAppTemplateRepository;
import com.clinic.security.AdminPrincipal;
import com.clinic.whatsapp.CampaignStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Marketing broadcasts to all patients (everyone is included by default). */
@Service
@RequiredArgsConstructor
public class WhatsAppCampaignService {

    private final WhatsAppCampaignRepository campaignRepository;
    private final WhatsAppTemplateRepository templateRepository;
    private final PatientRepository patientRepository;
    private final WhatsAppSettingsService settingsService;
    private final WhatsAppTemplateService templateService;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public AudienceResponse audience() {
        return new AudienceResponse(patientRepository.countByWhatsappOptInTrue());
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> recent() {
        return campaignRepository.findTop20ByOrderByCreatedAtDesc().stream().map(WhatsAppCampaignService::toResponse).toList();
    }

    @Transactional
    public CampaignResponse start(Long templateId) {
        WhatsAppTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("WhatsApp template", templateId));
        if (template.getPurpose() != TemplatePurpose.MARKETING) {
            throw new BusinessException("templateId", "Only marketing templates can be sent as a campaign");
        }
        if (!template.isEnabled()) {
            throw new BusinessException("templateId", "Switch this template on before sending it");
        }
        WhatsAppSettings settings = settingsService.load();
        if (!settings.canSendMessages()) {
            throw new BusinessException("WhatsApp is switched off. Turn it on in WhatsApp setup first.");
        }
        templateService.checkAgainstMeta(template, settings);
        long audience = patientRepository.countByWhatsappOptInTrue();
        if (audience == 0) {
            throw new BusinessException("There are no patients to send to yet.");
        }
        WhatsAppCampaign campaign = new WhatsAppCampaign();
        campaign.setTemplate(template);
        campaign.setTemplateLabel(template.getLabel());
        campaign.setTotal((int) audience);
        campaign.setCreatedBy(currentAdminEmail());
        WhatsAppCampaign saved = campaignRepository.save(campaign);
        audit.record("CAMPAIGN", "WhatsAppCampaign", saved.getId(), template.getLabel() + " to " + audience + " patients");
        events.publishEvent(new CampaignStartedEvent(saved.getId()));
        return toResponse(saved);
    }

    static CampaignResponse toResponse(WhatsAppCampaign c) {
        return new CampaignResponse(c.getId(), c.getTemplateLabel(), c.getStatus(), c.getTotal(), c.getSent(), c.getFailed(),
                c.getCreatedBy(), c.getCreatedAt(), c.getFinishedAt());
    }

    private static String currentAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AdminPrincipal p ? p.email() : null;
    }
}
