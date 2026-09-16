package com.fpt.ibom.cv;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.cv.controller.CvExportController;
import com.fpt.ibom.cv.model.CvExportResult;
import com.fpt.ibom.cv.service.CvExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CvExportController.class)
@Import({ CvExportController.class, SecurityConfig.class })
@ActiveProfiles("test")
class CvExportControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CvExportService exportService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void returnsPdfBytesWithAttachmentFilenameAndFixedRoute() throws Exception {
		byte[] bytes = new byte[] { 37, 80, 68, 70 };
		when(exportService.export(userPrincipal(), 8L, "pdf", null))
				.thenReturn(new CvExportResult(bytes, "Last_20260102.pdf", "pdf"));

		mockMvc.perform(get("/api/cv/download/8").param("format", "pdf").with(principal()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"Last_20260102.pdf\""))
				.andExpect(content().bytes(bytes));

		verify(exportService).export(userPrincipal(), 8L, "pdf", null);
	}

	@Test
	void returnsDocxBytesWithDocxMediaTypeAndOptionalFilenameFormat() throws Exception {
		byte[] bytes = new byte[] { 80, 75, 3, 4 };
		when(exportService.export(userPrincipal(), 8L, "docx", 42L))
				.thenReturn(new CvExportResult(bytes, "Explicit.docx", "docx"));

		mockMvc.perform(get("/api/cv/download/8").param("format", "docx").param("fileNameFormatId", "42")
					.with(principal()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"Explicit.docx\""))
				.andExpect(content().bytes(bytes));

		verify(exportService).export(userPrincipal(), 8L, "docx", 42L);
	}

	@Test
	void requiresAuthenticationAndDoesNotInvokeExportService() throws Exception {
		mockMvc.perform(get("/api/cv/download/8").param("format", "pdf"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(userPrincipal(), null, List.of()));
	}

	private UserPrincipal userPrincipal() {
		return new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);
	}
}
