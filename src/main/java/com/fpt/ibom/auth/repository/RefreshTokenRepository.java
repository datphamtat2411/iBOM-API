package com.fpt.ibom.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpt.ibom.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
