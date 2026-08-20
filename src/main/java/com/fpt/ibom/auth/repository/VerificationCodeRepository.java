package com.fpt.ibom.auth.repository;

import java.time.Instant;
import java.util.Optional;

import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
	long countByEmailAndPurposeAndCreatedAtAfter(String email, VerificationPurpose purpose, Instant createdAt);
	Optional<VerificationCode> findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(String email, VerificationPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select code from VerificationCode code where code.email = :email and code.purpose = :purpose and code.usedAt is null order by code.createdAt desc")
	Optional<VerificationCode> findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDescForUpdate(
			@Param("email") String email, @Param("purpose") VerificationPurpose purpose);
}
