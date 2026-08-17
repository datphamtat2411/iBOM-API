package com.fpt.ibom.validation;

import java.util.List;

public record ValidationErrorResponse(List<FieldViolation> errors) {

	public record FieldViolation(String field, String message) {
	}
}
