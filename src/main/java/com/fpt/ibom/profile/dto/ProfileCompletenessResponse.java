package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProfileCompletenessResponse(BigDecimal percentage, boolean completed,
		List<ProfileCompletenessSectionResponse> sections) {
}
