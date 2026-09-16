package com.fpt.ibom.cv;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.cv.controller.CvPreviewController;
import com.fpt.ibom.cv.service.CvPreviewService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CvPreviewController.class)
@Import({CvPreviewController.class, SecurityConfig.class})
@ActiveProfiles("test")
class CvPreviewControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CvPreviewService previewService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void returnsRawPdfBytesWithTheFixedPreviewRouteAndContentType() throws Exception {
		byte[] pdf = new byte[] { 37, 80, 68, 70, 45, 49 };
		when(previewService.preview(userPrincipal(), 8L)).thenReturn(pdf);

		mockMvc.perform(get("/api/cv/preview/8").with(principal()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(content().bytes(pdf));

		verify(previewService).preview(userPrincipal(), 8L);
	}

	@Test
	void requiresAuthenticationForPreview() throws Exception {
		mockMvc.perform(get("/api/cv/preview/8")).andExpect(status().isUnauthorized());
	}

	@Test
	void mapsCentralizedProfileErrorsWithoutWrappingSuccessfulBinaryResponses() throws Exception {
		when(previewService.preview(userPrincipal(), 8L)).thenThrow(
				new ApiException(HttpStatus.FORBIDDEN, ErrorCode.REQUEST_FAILED, "Forbidden"));

		mockMvc.perform(get("/api/cv/preview/8").with(principal()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"));

		when(previewService.preview(userPrincipal(), 9L)).thenThrow(
				new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));
		mockMvc.perform(get("/api/cv/preview/9").with(principal()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(userPrincipal(), null, List.of()));
	}

	private UserPrincipal userPrincipal() {
		return new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);
	}
}
