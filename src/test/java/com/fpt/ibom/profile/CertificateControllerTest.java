package com.fpt.ibom.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.controller.CertificateController;
import com.fpt.ibom.profile.dto.CertificateMutationResponse;
import com.fpt.ibom.profile.dto.CertificateResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.service.CertificateService;
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

@WebMvcTest(controllers = CertificateController.class)
@Import({ CertificateController.class, SecurityConfig.class })
@ActiveProfiles("test")
class CertificateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CertificateService certificateService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);

	@Test
	void mapsAllCertificateRoutesAndResponseWrappers() throws Exception {
		CertificateResponse certificate = new CertificateResponse(12L, "AWS", LocalDate.of(2024, 1, 1));
		when(certificateService.list(principal, 8L)).thenReturn(List.of(certificate));
		when(certificateService.create(eq(principal), eq(8L), any()))
				.thenReturn(new CertificateMutationResponse(certificate, 1L));
		when(certificateService.update(eq(principal), eq(8L), eq(12L), any()))
				.thenReturn(new CertificateMutationResponse(certificate, 2L));
		when(certificateService.delete(principal, 8L, 12L, 2L)).thenReturn(new ProfileVersionResponse(3L));

		mockMvc.perform(get("/api/profiles/8/certificates").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value(12))
				.andExpect(jsonPath("$.data[0].certificateName").value("AWS"));
		mockMvc.perform(post("/api/profiles/8/certificates").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.certificate.id").value(12))
				.andExpect(jsonPath("$.data.profileVersion").value(1));
		mockMvc.perform(put("/api/profiles/8/certificates/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content(requestJson(1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(2));
		mockMvc.perform(delete("/api/profiles/8/certificates/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(3));

		verify(certificateService).delete(principal, 8L, 12L, 2L);
	}

	@Test
	void requiresAuthenticationAndMutationFields() throws Exception {
		mockMvc.perform(get("/api/profiles/8/certificates")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/profiles/8/certificates").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(delete("/api/profiles/8/certificates/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void mapsCertificateBusinessErrorsAndRejectsInvalidFields() throws Exception {
		doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.CERTIFICATE_ALREADY_EXISTS, "Duplicate certificate"))
				.when(certificateService).create(eq(principal), eq(8L), any());

		mockMvc.perform(post("/api/profiles/8/certificates").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content(requestJson(0)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CERTIFICATE_ALREADY_EXISTS"));

		mockMvc.perform(post("/api/profiles/8/certificates").with(principal())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"certificateName\":\" \",\"version\":-1}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	private String requestJson(long version) {
		return "{\"certificateName\":\" AWS \",\"issueDate\":\"2024-01-01\",\"version\":" + version + "}";
	}
}
