package com.clinic.enums;

/** What a WhatsApp message template is used for. */
public enum TemplatePurpose {
    /** Sent after a patient books (status PENDING). */
    APPOINTMENT_RECEIVED,
    /** Sent when the clinic confirms an appointment. */
    APPOINTMENT_CONFIRMED,
    /** Sent when the clinic cancels an appointment. */
    APPOINTMENT_CANCELLED,
    /** Sent when the clinic moves an appointment to another date/time. */
    APPOINTMENT_RESCHEDULED,
    /** Promotional campaign sent to all patients. Any number of these can exist. */
    MARKETING;

    public boolean isAutomatic() {
        return this != MARKETING;
    }
}
