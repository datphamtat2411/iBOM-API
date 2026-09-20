package com.fpt.ibom.profile.service;

import java.util.List;

import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileEligibilityService {

	private final ProfileRepository profileRepository;

	public ProfileEligibilityService(ProfileRepository profileRepository) {
		this.profileRepository = profileRepository;
	}

	@Transactional(readOnly = true)
	public List<Profile> findEligibleMemberProfiles() {
		return profileRepository.findEligibleMemberProfiles();
	}
}
