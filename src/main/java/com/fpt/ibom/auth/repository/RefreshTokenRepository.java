package com.fpt.ibom.auth.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpt.ibom.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select refreshToken from RefreshToken refreshToken join fetch refreshToken.user where refreshToken.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Modifying
	@Query("update RefreshToken refreshToken set refreshToken.revokedAt = :revokedAt where refreshToken.user.id = :userId and refreshToken.revokedAt is null")
	void revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") java.time.Instant revokedAt);
}
