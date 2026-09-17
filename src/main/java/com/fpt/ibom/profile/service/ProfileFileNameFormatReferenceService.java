package com.fpt.ibom.profile.service;

import com.fpt.ibom.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileFileNameFormatReferenceService {

	private final ProfileRepository profileRepository;

	public ProfileFileNameFormatReferenceService(ProfileRepository profileRepository) {
		this.profileRepository = profileRepository;
	}

	public boolean isReferenced(Long fileNameFormatId) {
		return profileRepository.existsByPreferredFileNameFormatId(fileNameFormatId);
	}
}
