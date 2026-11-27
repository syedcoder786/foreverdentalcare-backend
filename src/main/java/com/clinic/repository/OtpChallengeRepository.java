package com.clinic.repository;

import com.clinic.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    Optional<OtpChallenge> findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    @Modifying
    @Query("update OtpChallenge c set c.consumed = true where c.phone = :phone and c.consumed = false")
    int consumeAllForPhone(@Param("phone") String phone);

    @Modifying
    @Query("delete from OtpChallenge c where c.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
