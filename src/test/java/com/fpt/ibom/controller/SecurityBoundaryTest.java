package com.fpt.ibom.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserAccountJwtAuthenticationConverter;
import com.fpt.ibom.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({SecurityConfig.class, UserAccountJwtAuthenticationConverter.class, SecurityBoundaryTest.ProtectedTestController.class})
class SecurityBoundaryTest extends AbstractControllerTest {

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk());
	}

	@Test
	void unauthenticatedRequestToProtectedEndpointUsesCommonUnauthorizedResponse() throws Exception {
		mockMvc.perform(get("/test/protected"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"))
				.andExpect(jsonPath("$.message").value("Unauthorized"))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void authenticatedRequestWithoutCsrfTokenIsAllowedForBearerApiFlow() throws Exception {
		mockMvc.perform(post("/test/protected").with(user("test-user")))
				.andExpect(status().isOk());
	}

	@RestController
	static class ProtectedTestController {

		@GetMapping("/test/protected")
		void protectedGet() {
		}

		@PostMapping("/test/protected")
		void protectedPost() {
		}
	}
}
