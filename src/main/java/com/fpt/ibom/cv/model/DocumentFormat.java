package com.fpt.ibom.cv.model;

import java.util.Locale;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DocumentFormat {
	PDF(".pdf"),
	DOCX(".docx");

	private final String extension;

	DocumentFormat(String extension) {
		this.extension = extension;
	}

	public String getExtension() {
		return extension;
	}

	public static DocumentFormat fromRequest(String value) {
		if (value == null || value.isBlank()) {
			throw invalidFormat();
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
		case "pdf" -> PDF;
		case "docx" -> DOCX;
		default -> throw invalidFormat();
		};
	}

	private static ApiException invalidFormat() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CV_EXPORT_FORMAT_INVALID,
				"Format must be pdf or docx");
	}
}
