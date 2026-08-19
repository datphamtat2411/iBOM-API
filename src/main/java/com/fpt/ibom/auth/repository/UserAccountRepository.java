package com.fpt.ibom.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpt.ibom.auth.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	Optional<UserAccount> findByEmailIgnoreCase(String email);
	boolean existsByEmailIgnoreCase(String email);
	boolean existsByUsernameIgnoreCase(String username);
}
