package com.fpt.ibom.cv.service;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.service.ProfileAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CvProfileAccessService {

	private final ProfileAccessService profileAccessService;

	public CvProfileAccessService(ProfileAccessService profileAccessService) {
		this.profileAccessService = profileAccessService;
	}

	@Transactional(readOnly = true)
	public Profile resolve(UserPrincipal principal, Long profileId) {
		return profileAccessService.resolve(principal, profileId);
	}
}
