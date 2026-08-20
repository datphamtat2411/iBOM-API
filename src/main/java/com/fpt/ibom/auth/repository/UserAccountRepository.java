package com.fpt.ibom.auth.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpt.ibom.auth.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	Optional<UserAccount> findByEmailIgnoreCase(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from UserAccount user where lower(user.email) = lower(:email)")
	Optional<UserAccount> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

	boolean existsByEmailIgnoreCase(String email);
	boolean existsByUsernameIgnoreCase(String username);
}
