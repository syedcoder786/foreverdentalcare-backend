package com.clinic.service;

import com.clinic.dto.AdminDtos.PatientDetail;
import com.clinic.dto.AdminDtos.PatientSummary;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.Patient;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.AppointmentSpecifications;
import com.clinic.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin-only patient directory. Patient data is never exposed through public APIs. */
@Service
@RequiredArgsConstructor
public class PatientAdminService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;

    @Transactional(readOnly = true)
    public PageResponse<PatientSummary> search(String search, Pageable pageable) {
        Page<Patient> page = patientRepository.findAll(matches(search), pageable);
        Map<Long, Long> counts = new HashMap<>();
        List<Long> ids = page.getContent().stream().map(Patient::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : appointmentRepository.countByPatientIds(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return PageResponse.from(page, p -> summary(p, counts.getOrDefault(p.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public PatientDetail get(Long id) {
        Patient p = patientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Patient", id));
        var appointments = appointmentRepository.findAll(AppointmentSpecifications.hasPatient(id),
                        Sort.by(Sort.Direction.DESC, "appointmentDate", "appointmentTime"))
                .stream().map(appointmentMapper::toResponse).toList();
        return new PatientDetail(summary(p, appointments.size()), appointments);
    }

    private static PatientSummary summary(Patient p, long count) {
        return new PatientSummary(p.getId(), p.getFullName(), p.getPhone(), p.getEmail(), p.getCreatedAt(), count);
    }

    private static Specification<Patient> matches(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            String term = search.trim().toLowerCase();
            String digits = term.replaceAll("\\D", "");
            var name = cb.like(cb.lower(root.get("fullName")), "%" + term.replace("%", "") + "%");
            return digits.isEmpty() ? name : cb.or(name, cb.like(root.get("phone"), "%" + digits + "%"));
        };
    }
}
