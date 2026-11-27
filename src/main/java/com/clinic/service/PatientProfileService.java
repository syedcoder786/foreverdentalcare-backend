package com.clinic.service;

import com.clinic.dto.PatientDtos.PatientProfileRequest;
import com.clinic.dto.PatientDtos.PatientProfileResponse;
import com.clinic.entity.Patient;
import com.clinic.exception.AuthenticationFailedException;
import com.clinic.repository.PatientRepository;
import com.clinic.util.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in patient's own profile. The mobile number is the sign-in identity and cannot be changed here. */
@Service
@RequiredArgsConstructor
public class PatientProfileService {

    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public PatientProfileResponse get(Long patientId) {
        return PatientAuthService.toProfile(load(patientId));
    }

    @Transactional
    public PatientProfileResponse update(Long patientId, PatientProfileRequest r) {
        Patient patient = load(patientId);
        patient.setFullName(r.fullName().trim());
        String email = ValidationPatterns.trimToNull(r.email());
        patient.setEmail(email == null ? null : email.toLowerCase());
        if (r.whatsappOptIn() != null) {
            patient.changeWhatsappOptIn(r.whatsappOptIn());
        }
        return PatientAuthService.toProfile(patient);
    }

    private Patient load(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new AuthenticationFailedException("Please sign in again."));
    }
}
