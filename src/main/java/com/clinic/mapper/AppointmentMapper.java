package com.clinic.mapper;

import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.BookingConfirmationResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.DentalService;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

    public AppointmentResponse toResponse(Appointment a) {
        Patient p = a.getPatient();
        DentalService s = a.getService();
        Doctor d = a.getDoctor();
        return new AppointmentResponse(
                a.getId(),
                new AppointmentResponse.PatientSummary(p.getId(), p.getFullName(), p.getPhone(), p.getEmail()),
                new AppointmentResponse.ServiceSummary(s.getId(), s.getName(), s.getEstimatedDuration(),
                        s.getSpecialty().getId(), s.getSpecialty().getName()),
                new AppointmentResponse.DoctorSummary(d.getId(), d.getFullName(), d.getSlug()),
                a.getAppointmentDate(),
                a.getAppointmentTime(),
                a.getEndTime(),
                a.getStatus(),
                a.getMessage(),
                a.getStatus().allowedTransitions(),
                a.getStatus().isReschedulable(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getConsultationFee(),
                a.getFirstVisitDiscountPercent(),
                a.getTreatmentFee(),
                a.getTreatmentDiscountPercent(),
                a.finalFee(),
                a.isOnlineBooking());
    }

    public BookingConfirmationResponse toConfirmation(Appointment a) {
        return new BookingConfirmationResponse(
                a.getId(),
                a.getStatus(),
                a.getService().getName(),
                a.getDoctor().getFullName(),
                a.getAppointmentDate(),
                a.getAppointmentTime(),
                a.getEndTime(),
                a.getConsultationFee(),
                a.getFirstVisitDiscountPercent(),
                a.getTreatmentFee(),
                a.getTreatmentDiscountPercent(),
                a.finalFee());
    }
}
