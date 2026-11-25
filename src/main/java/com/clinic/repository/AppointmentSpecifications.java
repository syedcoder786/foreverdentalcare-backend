package com.clinic.repository;

import com.clinic.entity.Appointment;
import com.clinic.entity.Patient;
import com.clinic.enums.AppointmentStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Composable filters for the admin appointment listing and calendars. */
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    public static Specification<Appointment> hasStatus(AppointmentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Appointment> hasDoctor(Long doctorId) {
        return (root, query, cb) -> doctorId == null ? null : cb.equal(root.get("doctor").get("id"), doctorId);
    }

    public static Specification<Appointment> hasSpecialty(Long specialtyId) {
        return (root, query, cb) -> specialtyId == null ? null
                : cb.equal(root.get("service").get("specialty").get("id"), specialtyId);
    }

    public static Specification<Appointment> hasPatient(Long patientId) {
        return (root, query, cb) -> patientId == null ? null : cb.equal(root.get("patient").get("id"), patientId);
    }

    public static Specification<Appointment> onOrAfter(LocalDate from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("appointmentDate"), from);
    }

    public static Specification<Appointment> onOrBefore(LocalDate to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("appointmentDate"), to);
    }

    /** Case-insensitive match on patient name, or a digits match on phone. */
    public static Specification<Appointment> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            Join<Appointment, Patient> patient = root.join("patient", JoinType.INNER);
            String term = search.trim().toLowerCase();
            String namePattern = "%" + escapeLike(term) + "%";
            String digits = term.replaceAll("\\D", "");
            var nameMatch = cb.like(cb.lower(patient.get("fullName")), namePattern, '\\');
            if (digits.isEmpty()) {
                return nameMatch;
            }
            return cb.or(nameMatch, cb.like(patient.get("phone"), "%" + digits + "%"));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
