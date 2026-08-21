package com.fpt.ibom.common;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fpt.ibom.exception.ErrorCode;

public record ApiResponse<T>(int code, @JsonInclude(JsonInclude.Include.NON_NULL) ErrorCode errorCode, String message,
		T data, Instant timestamp) {

	public ApiResponse(int code, String message, T data) {
		this(code, null, message, data, Instant.now());
	}

	public ApiResponse(int code, ErrorCode errorCode, String message, T data) {
		this(code, errorCode, message, data, Instant.now());
	}
}
