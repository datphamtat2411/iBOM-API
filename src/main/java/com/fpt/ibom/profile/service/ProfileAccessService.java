package com.fpt.ibom.profile.service;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileAccessService {

	private final ProfileRepository profileRepository;

	public ProfileAccessService(ProfileRepository profileRepository) {
		this.profileRepository = profileRepository;
	}

	@Transactional(readOnly = true)
	public Profile resolve(UserPrincipal principal, Long profileId) {
		Profile profile = profileRepository.findByIdAndDeletedAtIsNull(profileId).orElseThrow(this::profileNotFound);
		if (principal.userId().equals(profile.getUser().getId())
				|| isManagerOrAdmin(principal) && profile.getUser().getRole() == UserRole.MEMBER) {
			return profile;
		}
		throw profileNotFound();
	}

	@Transactional(readOnly = true)
	public Profile resolveOwned(UserPrincipal principal, Long profileId) {
		return profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, principal.userId())
				.orElseThrow(this::profileNotFound);
	}

	private boolean isManagerOrAdmin(UserPrincipal principal) {
		return principal.role() == UserRole.MANAGER || principal.role() == UserRole.ADMIN;
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}

}
