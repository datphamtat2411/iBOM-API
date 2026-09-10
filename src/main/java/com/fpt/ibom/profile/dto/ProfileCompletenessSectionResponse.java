package com.fpt.ibom.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileCompletenessSectionResponse(String key, int weight, boolean completed,
		Integer validFieldCount, Integer fieldCount, Boolean hasQualifyingRecord) {
}
