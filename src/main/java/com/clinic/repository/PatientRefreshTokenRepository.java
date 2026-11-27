package com.clinic.repository;

import com.clinic.entity.PatientRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PatientRefreshTokenRepository extends JpaRepository<PatientRefreshToken, Long> {

    Optional<PatientRefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update PatientRefreshToken t set t.revoked = true where t.patient.id = :patientId and t.revoked = false")
    int revokeAllForPatient(@Param("patientId") Long patientId);

    @Modifying
    @Query("delete from PatientRefreshToken t where t.expiresAt < :cutoff or t.revoked = true")
    int deleteExpiredOrRevoked(@Param("cutoff") Instant cutoff);
}
