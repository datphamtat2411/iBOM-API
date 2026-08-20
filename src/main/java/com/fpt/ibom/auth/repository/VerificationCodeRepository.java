package com.fpt.ibom.auth.repository;

import java.time.Instant;
import java.util.Optional;

import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
	long countByEmailAndPurposeAndCreatedAtAfter(String email, VerificationPurpose purpose, Instant createdAt);
	Optional<VerificationCode> findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(String email, VerificationPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<VerificationCode> findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(String email,
			VerificationPurpose purpose);
}
