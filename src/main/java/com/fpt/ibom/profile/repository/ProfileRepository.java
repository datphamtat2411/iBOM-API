package com.fpt.ibom.profile.repository;

import com.fpt.ibom.profile.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
	boolean existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(Long userId, String profileName);
}
