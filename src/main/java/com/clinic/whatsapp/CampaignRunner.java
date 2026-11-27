package com.clinic.whatsapp;

import com.clinic.config.AsyncConfig;
import com.clinic.entity.Patient;
import com.clinic.entity.WhatsAppCampaign;
import com.clinic.entity.WhatsAppSettings;
import com.clinic.entity.WhatsAppTemplate;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.WhatsAppCampaignRepository;
import com.clinic.repository.WhatsAppSettingsRepository;
import com.clinic.repository.WhatsAppTemplateRepository;
import com.clinic.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Sends a marketing campaign in the background, one patient at a time (lightly throttled), and keeps the sent/failed
 * counts up to date so the admin can follow progress.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignRunner {

    private static final long PAUSE_BETWEEN_MESSAGES_MS = 100;

    private final WhatsAppCampaignRepository campaignRepository;
    private final WhatsAppTemplateRepository templateRepository;
    private final WhatsAppSettingsRepository settingsRepository;
    private final PatientRepository patientRepository;
    private final WhatsAppClient client;
    private final TemplateValueResolver resolver;

    @Async(AsyncConfig.WHATSAPP_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void run(CampaignStartedEvent event) {
        WhatsAppCampaign campaign = campaignRepository.findById(event.campaignId()).orElse(null);
        if (campaign == null) {
            return;
        }
        try {
            WhatsAppSettings settings = settingsRepository.findById(WhatsAppSettings.SINGLETON_ID).orElseThrow();
            WhatsAppTemplate template = templateRepository.findById(campaign.getTemplate().getId()).orElseThrow();
            WhatsAppClient.TemplateInfo info = client.describe(settings, template.getTemplateName(), template.getTemplateLanguage());
            List<Patient> patients = patientRepository.findByWhatsappOptInTrueOrderByIdAsc();
            campaign.setTotal(patients.size());
            int processed = 0;
            for (Patient patient : patients) {
                try {
                    String phone = PhoneNumbers.toE164(patient.getPhone(), settings.getDefaultCountryCode());
                    client.sendTemplate(settings, phone, info, resolver.resolve(template, TemplateValueResolver.MessageContext.of(patient)));
                    campaign.setSent(campaign.getSent() + 1);
                } catch (Exception ex) {
                    campaign.setFailed(campaign.getFailed() + 1);
                    log.warn("Campaign {}: message to patient {} failed: {}", campaign.getId(), patient.getId(), ex.getMessage());
                }
                if (++processed % 10 == 0) {
                    campaign = campaignRepository.save(campaign);
                }
                Thread.sleep(PAUSE_BETWEEN_MESSAGES_MS);
            }
            campaign.setStatus(campaign.getSent() == 0 && campaign.getFailed() > 0 ? WhatsAppCampaign.Status.FAILED : WhatsAppCampaign.Status.COMPLETED);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            campaign.setStatus(WhatsAppCampaign.Status.FAILED);
        } catch (Exception ex) {
            log.warn("Campaign {} stopped: {}", campaign.getId(), ex.getMessage());
            campaign.setStatus(WhatsAppCampaign.Status.FAILED);
        }
        campaign.setFinishedAt(Instant.now());
        campaignRepository.save(campaign);
        log.info("Campaign {} finished: {} sent, {} failed", campaign.getId(), campaign.getSent(), campaign.getFailed());
    }
}
