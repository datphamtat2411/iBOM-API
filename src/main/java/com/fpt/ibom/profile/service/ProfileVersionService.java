package com.fpt.ibom.profile.service;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileVersionService {
	private final ProfileRepository profileRepository;
	private final EntityManager entityManager;

	public ProfileVersionService(ProfileRepository profileRepository, EntityManager entityManager) {
		this.profileRepository = profileRepository;
		this.entityManager = entityManager;
	}

	public long advance(Profile profile) {
		long expectedVersion = profile.getVersion();
		int updated = profileRepository.advanceVersion(profile.getId(), expectedVersion);
		if (updated != 1) {
			throw versionConflict();
		}
		entityManager.refresh(profile);
		return profile.getVersion();
	}

	@Transactional
	public void markPreviewed(Profile profile, long expectedVersion) {
		int updated = profileRepository.markPreviewed(profile.getId(), expectedVersion);
		if (updated != 1) {
			throw versionConflict();
		}
		entityManager.refresh(profile);
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request");
	}
}
