package com.fpt.ibom.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final ErrorCode errorCode;

	public ApiException(HttpStatus status, String message) {
		this(status, ErrorCode.REQUEST_FAILED, message);
	}

	public ApiException(HttpStatus status, ErrorCode errorCode, String message) {
		super(message);
		this.status = status;
		this.errorCode = errorCode;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
