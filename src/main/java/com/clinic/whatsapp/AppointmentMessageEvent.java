package com.clinic.whatsapp;

import com.clinic.enums.TemplatePurpose;

/** Published when an appointment changes; a WhatsApp message is sent after the transaction commits. */
public record AppointmentMessageEvent(Long appointmentId, TemplatePurpose purpose) {
}
