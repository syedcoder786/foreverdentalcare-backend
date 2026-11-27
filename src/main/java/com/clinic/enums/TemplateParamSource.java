package com.clinic.enums;

/** What fills a template placeholder ({{1}}, {{2}}...). Appointment values exist only for appointment messages. */
public enum TemplateParamSource {
    PATIENT_NAME(false),
    PATIENT_FIRST_NAME(false),
    SERVICE_NAME(true),
    DOCTOR_NAME(true),
    APPOINTMENT_DATE(true),
    APPOINTMENT_TIME(true),
    BOOKING_REFERENCE(true),
    CLINIC_NAME(false),
    CLINIC_PHONE(false),
    CLINIC_ADDRESS(false),
    /** A fixed text typed by the admin (e.g. an offer, a coupon code or a link path). */
    CUSTOM_TEXT(false);

    private final boolean appointmentOnly;

    TemplateParamSource(boolean appointmentOnly) {
        this.appointmentOnly = appointmentOnly;
    }

    public boolean isAppointmentOnly() {
        return appointmentOnly;
    }
}
