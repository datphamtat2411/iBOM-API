package com.fpt.ibom.member.repository;

import java.time.Instant;

import com.fpt.ibom.auth.entity.UserStatus;

public interface MemberSummaryProjection {
	Long getId();
	String getUsername();
	String getEmail();
	UserStatus getStatus();
	Long getActiveProfileCount();
	Instant getAccountUpdatedAt();
	Instant getProfileUpdatedAt();
}
