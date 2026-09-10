package com.fpt.ibom.profile.dto;

import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.LanguageLevel;

public record ProfileLanguageResponse(Long profileLanguageId, Long languageId, String languageName, LanguageLevel level) {

	public static ProfileLanguageResponse from(ProfileLanguage profileLanguage) {
		return new ProfileLanguageResponse(profileLanguage.getId(), profileLanguage.getLanguage().getId(),
				profileLanguage.getLanguage().getName(), profileLanguage.getLevel());
	}
}
