package com.clinic.whatsapp;

import com.clinic.config.AsyncConfig;
import com.clinic.entity.Appointment;
import com.clinic.entity.WhatsAppSettings;
import com.clinic.entity.WhatsAppTemplate;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.WhatsAppSettingsRepository;
import com.clinic.repository.WhatsAppTemplateRepository;
import com.clinic.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the automatic WhatsApp message for an appointment change (received / confirmed / cancelled / rescheduled)
 * when an admin has switched that message on. Runs in the background after the change is committed; failures are
 * logged and never affect the booking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentMessageSender {

    private final WhatsAppSettingsRepository settingsRepository;
    private final WhatsAppTemplateRepository templateRepository;
    private final AppointmentRepository appointmentRepository;
    private final WhatsAppClient client;
    private final TemplateValueResolver resolver;

    @Async(AsyncConfig.WHATSAPP_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onAppointmentChanged(AppointmentMessageEvent event) {
        try {
            WhatsAppSettings settings = settingsRepository.findById(WhatsAppSettings.SINGLETON_ID).orElse(null);
            if (settings == null || !settings.canSendMessages()) {
                return;
            }
            WhatsAppTemplate template = templateRepository.findFirstByPurposeOrderByIdAsc(event.purpose()).orElse(null);
            if (template == null || !template.isEnabled() || template.getTemplateName() == null) {
                return;
            }
            Appointment appointment = appointmentRepository.findWithDetailsById(event.appointmentId()).orElse(null);
            if (appointment == null) {
                return;
            }
            String phone = PhoneNumbers.toE164(appointment.getPatient().getPhone(), settings.getDefaultCountryCode());
            WhatsAppClient.TemplateInfo info = client.describe(settings, template.getTemplateName(), template.getTemplateLanguage());
            client.sendTemplate(settings, phone, info,
                    resolver.resolve(template, TemplateValueResolver.MessageContext.of(appointment.getPatient(), appointment)));
            log.info("WhatsApp '{}' message sent for appointment {}", template.getLabel(), appointment.getId());
        } catch (Exception ex) {
            log.warn("WhatsApp {} message for appointment {} not sent: {}", event.purpose(), event.appointmentId(), ex.getMessage());
        }
    }
}
