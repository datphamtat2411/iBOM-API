package com.fpt.ibom.common;

import java.time.Instant;

public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

	public ApiResponse(int code, String message, T data) {
		this(code, message, data, Instant.now());
	}
}
