package com.fpt.ibom.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Import({GlobalExceptionHandler.class, HealthControllerTest.ApiTestController.class})
class HealthControllerTest extends AbstractControllerTest {

	@Test
	void healthReturnsCommonSuccessResponse() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.message").value("Success"))
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void validationFailureUsesCommonErrorResponse() throws Exception {
		mockMvc.perform(post("/test/validation").contentType("application/json").content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.data.errors[0].field").value("name"))
				.andExpect(jsonPath("$.data.errors[0].message").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void apiExceptionUsesItsHttpStatusAndCommonErrorResponse() throws Exception {
		mockMvc.perform(get("/test/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(409))
				.andExpect(jsonPath("$.message").value("Conflict"))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void unexpectedExceptionUsesInternalServerErrorResponse() throws Exception {
		mockMvc.perform(get("/test/error"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value(500))
				.andExpect(jsonPath("$.message").value("Internal server error"))
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@RestController
	static class ApiTestController {

		@PostMapping("/test/validation")
		void validate(@Valid @RequestBody ValidationRequest request) {
		}

		@GetMapping("/test/conflict")
		void conflict() {
			throw new ApiException(HttpStatus.CONFLICT, "Conflict");
		}

		@GetMapping("/test/error")
		void error() {
			throw new IllegalStateException("Unexpected");
		}
	}

	record ValidationRequest(@NotBlank String name) {
	}
}
