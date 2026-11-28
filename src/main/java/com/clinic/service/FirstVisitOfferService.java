package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.ClinicSettings;
import com.clinic.enums.AppointmentStatus;
import com.clinic.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Online first-visit discount (percent off the consultation fee, set in Admin -> Clinic settings).
 * <p>
 * Rules:
 * <ul>
 *   <li>Only appointments with a consultation fee (treatment fee, else the doctor's) can carry it: there is nothing
 *       to take a percentage of otherwise.</li>
 *   <li>Only appointments booked by the patient on the website can carry it.</li>
 *   <li>A patient gets it while they have never had a confirmed or completed appointment, and at most one active
 *       appointment carries it at a time.</li>
 *   <li>If the clinic confirms a different online booking first, the discount moves to that one.</li>
 *   <li>If the appointment carrying it is cancelled (and the patient still has no confirmed visit), it passes to their
 *       earliest other pending online booking, or is offered again on their next booking. The cancelled appointment
 *       keeps its percent for history.</li>
 * </ul>
 * Called inside the caller's transaction; changes to managed appointments are flushed on commit.
 */
@Service
@RequiredArgsConstructor
public class FirstVisitOfferService {

    private static final Set<AppointmentStatus> VISITED = EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);
    private static final Set<AppointmentStatus> ACTIVE =
            EnumSet.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);

    private final AppointmentRepository repository;
    private final ClinicSettingsService settingsService;

    /** For a new appointment, before it is saved (patient must already be persisted). */
    public void applyOnBooking(Appointment appointment) {
        ClinicSettings s = settingsService.getSettings();
        if (!appointment.isOnlineBooking() || !hasFee(appointment)
                || !s.isFirstVisitOfferEnabled() || s.getFirstVisitDiscountPercent() == null) {
            return;
        }
        Long patientId = appointment.getPatient().getId();
        long self = idOf(appointment);
        if (repository.existsByPatientIdAndStatusInAndIdNot(patientId, VISITED, self)) {
            return;
        }
        if (repository.findFirstByPatientIdAndStatusInAndFirstVisitDiscountPercentIsNotNullAndIdNot(patientId, ACTIVE, self).isPresent()) {
            return;
        }
        appointment.setFirstVisitDiscountPercent(s.getFirstVisitDiscountPercent());
    }

    /** After the appointment's status has been changed to {@code target}. */
    public void onStatusChanged(Appointment appointment, AppointmentStatus target) {
        Long patientId = appointment.getPatient().getId();
        long self = idOf(appointment);
        boolean visitedBefore = repository.existsByPatientIdAndStatusInAndIdNot(patientId, VISITED, self);
        if (visitedBefore) {
            return;
        }
        if (target == AppointmentStatus.CONFIRMED && appointment.getFirstVisitDiscountPercent() == null
                && appointment.isOnlineBooking() && hasFee(appointment)) {
            repository.findFirstByPatientIdAndStatusInAndFirstVisitDiscountPercentIsNotNullAndIdNot(
                            patientId, EnumSet.of(AppointmentStatus.PENDING), self)
                    .ifPresent(holder -> {
                        appointment.setFirstVisitDiscountPercent(holder.getFirstVisitDiscountPercent());
                        holder.setFirstVisitDiscountPercent(null);
                    });
        } else if (target == AppointmentStatus.CANCELLED && appointment.getFirstVisitDiscountPercent() != null) {
            Integer percent = appointment.getFirstVisitDiscountPercent();
            repository.findFirstByPatientIdAndStatusAndOnlineBookingTrueAndConsultationFeeGreaterThanAndFirstVisitDiscountPercentIsNullAndIdNotOrderByAppointmentDateAscAppointmentTimeAsc(
                            patientId, AppointmentStatus.PENDING, BigDecimal.ZERO, self)
                    .ifPresent(next -> next.setFirstVisitDiscountPercent(percent));
        }
    }

    private static boolean hasFee(Appointment a) {
        return a.getConsultationFee() != null && a.getConsultationFee().signum() > 0;
    }

    private static long idOf(Appointment a) {
        return a.getId() == null ? -1L : a.getId();
    }
}
