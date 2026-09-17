package com.fpt.ibom.master.dto;

import java.time.Instant;

import com.fpt.ibom.master.entity.FileNameFormat;

public record FileNameFormatResponse(Long id, String name, String pattern, boolean isDefault, Instant createdAt,
		Instant updatedAt) {

	public static FileNameFormatResponse from(FileNameFormat format) {
		return new FileNameFormatResponse(format.getId(), format.getName(), format.getPattern(), format.isDefault(),
				format.getCreatedAt(), format.getUpdatedAt());
	}
}
