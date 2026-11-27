package com.clinic.whatsapp;

import com.clinic.entity.Appointment;
import com.clinic.entity.ClinicSettings;
import com.clinic.entity.JsonValues.TemplateParam;
import com.clinic.entity.Patient;
import com.clinic.entity.WhatsAppTemplate;
import com.clinic.enums.TemplateParamSource;
import com.clinic.service.ClinicSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Turns a template's placeholder mapping into the actual values for one patient / appointment. */
@Component
@RequiredArgsConstructor
public class TemplateValueResolver {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final ClinicSettingsService clinicSettingsService;

    /** The data available to a message. Appointment fields are null for marketing messages. */
    public record MessageContext(String patientName, String serviceName, String doctorName,
                                 LocalDate date, LocalTime time, Long reference) {

        public static MessageContext of(Patient patient, Appointment a) {
            return new MessageContext(patient.getFullName(), a.getService().getName(), a.getDoctor().getFullName(),
                    a.getAppointmentDate(), a.getAppointmentTime(), a.getId());
        }

        public static MessageContext of(Patient patient) {
            return new MessageContext(patient.getFullName(), null, null, null, null, null);
        }

        /** Example data for "Send test". */
        public static MessageContext sample() {
            return new MessageContext("Rahul Sharma", "Dental check-up", "Dr. S. M. Akif",
                    LocalDate.now().plusDays(1), LocalTime.of(10, 30), 1024L);
        }
    }

    public WhatsAppClient.TemplateValues resolve(WhatsAppTemplate template, MessageContext ctx) {
        ClinicSettings clinic = clinicSettingsService.getSettings();
        return new WhatsAppClient.TemplateValues(
                values(template.getHeaderParams(), ctx, clinic),
                template.getHeaderMediaUrl(),
                values(template.getBodyParams(), ctx, clinic),
                values(template.getButtonParams(), ctx, clinic));
    }

    private List<String> values(List<TemplateParam> params, MessageContext ctx, ClinicSettings clinic) {
        return params == null ? List.of() : params.stream().map(p -> value(p, ctx, clinic)).toList();
    }

    private static String value(TemplateParam p, MessageContext ctx, ClinicSettings clinic) {
        TemplateParamSource source;
        try {
            source = TemplateParamSource.valueOf(p.source());
        } catch (RuntimeException ex) {
            return "-";
        }
        String value = switch (source) {
            case PATIENT_NAME -> ctx.patientName();
            case PATIENT_FIRST_NAME -> ctx.patientName() == null ? null : ctx.patientName().trim().split("\\s+")[0];
            case SERVICE_NAME -> ctx.serviceName();
            case DOCTOR_NAME -> ctx.doctorName();
            case APPOINTMENT_DATE -> ctx.date() == null ? null : DATE.format(ctx.date());
            case APPOINTMENT_TIME -> ctx.time() == null ? null : TIME.format(ctx.time());
            case BOOKING_REFERENCE -> ctx.reference() == null ? null : String.valueOf(ctx.reference());
            case CLINIC_NAME -> clinic.getClinicName();
            case CLINIC_PHONE -> clinic.getPhone();
            case CLINIC_ADDRESS -> clinic.getAddress();
            case CUSTOM_TEXT -> p.text();
        };
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
