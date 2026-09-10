package com.fpt.ibom.profile.dto;

import java.util.List;

public record ProfileCompletenessResponse(int percentage, boolean completed,
		List<ProfileCompletenessSectionResponse> sections) {
}
