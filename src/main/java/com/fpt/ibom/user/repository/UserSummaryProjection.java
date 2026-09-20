package com.fpt.ibom.user.repository;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;

public interface UserSummaryProjection {
	Long getId();
	String getUsername();
	String getEmail();
	UserRole getRole();
	UserStatus getStatus();
}
