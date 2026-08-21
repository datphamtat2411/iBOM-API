package com.fpt.ibom.exception;

import java.util.List;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.validation.ValidationErrorResponse;
import com.fpt.ibom.validation.ValidationErrorResponse.FieldViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException exception) {
		HttpStatus status = exception.getStatus();
		return ResponseEntity.status(status)
				.body(new ApiResponse<>(status.value(), exception.getErrorCode(), exception.getMessage(), null));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException exception) {
		List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldViolation)
				.toList();
		ValidationErrorResponse validationErrors = new ValidationErrorResponse(errors);
		return ResponseEntity.badRequest()
				.body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_ERROR, "Validation failed",
						validationErrors));
	}

	@ExceptionHandler(ErrorResponseException.class)
	public ResponseEntity<ApiResponse<Object>> handleErrorResponseException(ErrorResponseException exception) {
		HttpStatusCode status = exception.getStatusCode();
		return ResponseEntity.status(status)
				.body(new ApiResponse<>(status.value(), ErrorCode.REQUEST_FAILED, messageFor(status), null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.INTERNAL_SERVER_ERROR,
						"Internal server error", null));
	}

	private FieldViolation toFieldViolation(FieldError error) {
		return new FieldViolation(error.getField(), error.getDefaultMessage());
	}

	private String messageFor(HttpStatusCode status) {
		return HttpStatus.resolve(status.value()) != null
				? HttpStatus.valueOf(status.value()).getReasonPhrase()
				: "Request failed";
	}
}
