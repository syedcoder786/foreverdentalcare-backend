package com.clinic.repository;

import com.clinic.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    Optional<Patient> findByPhone(String phone);

    List<Patient> findByPhoneIn(Collection<String> phones);

    List<Patient> findByWhatsappOptInTrueOrderByIdAsc();

    long countByWhatsappOptInTrue();
}
