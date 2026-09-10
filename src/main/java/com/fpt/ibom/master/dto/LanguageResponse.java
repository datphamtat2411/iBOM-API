package com.fpt.ibom.master.dto;

import java.time.Instant;

import com.fpt.ibom.master.entity.Language;

public record LanguageResponse(Long id, String name, Instant createdAt, Instant updatedAt) {

	public static LanguageResponse from(Language language) {
		return new LanguageResponse(language.getId(), language.getName(), language.getCreatedAt(), language.getUpdatedAt());
	}
}
