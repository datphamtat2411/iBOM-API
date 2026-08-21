package com.fpt.ibom.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.fpt.ibom.auth.security.UserAccountJwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import({GlobalExceptionHandler.class, SecurityConfig.class, HealthControllerTest.ApiTestController.class})
class HealthControllerTest extends AbstractControllerTest {
	@MockitoBean
	private UserAccountJwtAuthenticationConverter jwtAuthenticationConverter;

	@Test
	void healthReturnsCommonSuccessResponse() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.message").value("Success"))
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.errorCode").doesNotExist())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void validationFailureUsesCommonErrorResponse() throws Exception {
		mockMvc.perform(post("/test/validation").with(user("test-user"))
				.contentType("application/json").content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.data.errors[0].field").value("name"))
				.andExpect(jsonPath("$.data.errors[0].message").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void apiExceptionExposesErrorCodeIndependentlyOfMessage() throws Exception {
		mockMvc.perform(get("/test/conflict").with(user("test-user")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(409))
				.andExpect(jsonPath("$.errorCode").value("AUTH_EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value("Conflict"))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void unexpectedExceptionUsesInternalServerErrorResponse() throws Exception {
		mockMvc.perform(get("/test/error").with(user("test-user")))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value(500))
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.message").value("Internal server error"))
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void frameworkExceptionUsesGenericRequestFailedCode() throws Exception {
		mockMvc.perform(get("/test/not-found").with(user("test-user")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(404))
				.andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"))
				.andExpect(jsonPath("$.message").value("Not Found"));
	}

	@RestController
	static class ApiTestController {

		@PostMapping("/test/validation")
		void validate(@Valid @RequestBody ValidationRequest request) {
		}

		@GetMapping("/test/conflict")
		void conflict() {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED, "Conflict");
		}

		@GetMapping("/test/error")
		void error() {
			throw new IllegalStateException("Unexpected");
		}

		@GetMapping("/test/not-found")
		void notFound() {
			throw new ErrorResponseException(HttpStatus.NOT_FOUND);
		}
	}

	record ValidationRequest(@NotBlank String name) {
	}
}
