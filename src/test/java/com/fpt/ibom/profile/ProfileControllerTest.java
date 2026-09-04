package com.fpt.ibom.profile;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.controller.ProfileController;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.service.ProfileService;
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

@WebMvcTest(controllers = ProfileController.class)
@Import({ProfileController.class, SecurityConfig.class})
@ActiveProfiles("test")
class ProfileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProfileService profileService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void createsProfileWithCurrentAboutMeContract() throws Exception {
		when(profileService.create(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new com.fpt.ibom.profile.dto.ProfileResponse(8L, 7L, "Default", "First", "Last",
					"Engineer", null, "Friendly", "Summary", false, 0L, null, null));

		mockMvc.perform(post("/api/profiles").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(profileJson("First", "Last", "Engineer", "Friendly", "Summary")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.firstName").value("First"))
				.andExpect(jsonPath("$.data.lastName").value("Last"))
				.andExpect(jsonPath("$.code").value(201))
				.andExpect(jsonPath("$.data.fullName").doesNotExist())
				.andExpect(jsonPath("$.data.email").doesNotExist())
				.andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
				.andExpect(jsonPath("$.data.address").doesNotExist());
		verify(profileService).create(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void acceptsMaximumAboutMeFieldLengthsOnCreateAndUpdate() throws Exception {
		String firstName = "x".repeat(100);
		String lastName = "x".repeat(100);
		String jobTitle = "x".repeat(100);
		String personality = "x".repeat(4000);
		String summary = "x".repeat(4000);
		when(profileService.create(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new com.fpt.ibom.profile.dto.ProfileResponse(8L, 7L, "Default", firstName, lastName,
					jobTitle, null, personality, summary, false, 0L, null, null));
		when(profileService.update(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(8L),
				org.mockito.ArgumentMatchers.any())).thenReturn(new ProfileDetailResponse(8L, "Default", firstName,
					lastName, jobTitle, null, personality, summary, false, 1L, null, null));

		mockMvc.perform(post("/api/profiles").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(profileJson(firstName, lastName, jobTitle, personality, summary)))
				.andExpect(status().isCreated());
		mockMvc.perform(put("/api/profiles/8").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(updateJson(firstName, lastName, jobTitle, personality, summary)))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsAboutMeFieldsAboveMaximumAndNegativeExperience() throws Exception {
		String[][] invalidRequests = {
				{"firstName", "x".repeat(101)}, {"lastName", "x".repeat(101)},
				{"jobTitle", "x".repeat(101)}, {"personality", "x".repeat(4001)},
				{"technicalSummary", "x".repeat(4001)}, {"yearsOfExperience", "-0.1"}
		};
		for (String[] invalid : invalidRequests) {
			String json = invalid[0].equals("yearsOfExperience")
					? profileJson("First", "Last", "Engineer", "Friendly", "Summary", invalid[1])
					: profileJson("firstName".equals(invalid[0]) ? invalid[1] : "First",
							"lastName".equals(invalid[0]) ? invalid[1] : "Last",
							"jobTitle".equals(invalid[0]) ? invalid[1] : "Engineer",
							"personality".equals(invalid[0]) ? invalid[1] : "Friendly",
							"technicalSummary".equals(invalid[0]) ? invalid[1] : "Summary");
			mockMvc.perform(post("/api/profiles").with(principal()).contentType(MediaType.APPLICATION_JSON)
					.content(json)).andExpect(status().isBadRequest());
			mockMvc.perform(put("/api/profiles/8").with(principal()).contentType(MediaType.APPLICATION_JSON)
					.content(json.replace("}", ",\"version\":0}"))).andExpect(status().isBadRequest());
		}
	}

	@Test
	void rejectsMissingOrBlankRequiredFields() throws Exception {
		String[] requiredFields = { "profileName", "firstName", "lastName", "jobTitle", "yearsOfExperience",
				"personality", "technicalSummary" };
		for (String field : requiredFields) {
			String value = field.equals("yearsOfExperience") ? "null" : "\"   \"";
			String json = profileJson("First", "Last", "Engineer", "Friendly", "Summary")
					.replace(field.equals("yearsOfExperience") ? "3.5" : "\"Default\"", value);
			mockMvc.perform(post("/api/profiles").with(principal()).contentType(MediaType.APPLICATION_JSON)
					.content(json)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		}
	}

	@Test
	void requiresAuthenticationForProfileReads() throws Exception {
		mockMvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/profiles/8")).andExpect(status().isUnauthorized());
	}

	@Test
	void returnsProfileListWithoutOwnershipOrDeletionFields() throws Exception {
		when(profileService.list(7L)).thenReturn(List.of(new ProfileSummaryResponse(8L, "Default", "First", "Last",
				"Engineer", null)));

		mockMvc.perform(get("/api/profiles/me").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value(8))
				.andExpect(jsonPath("$.data[0].profileName").value("Default"))
				.andExpect(jsonPath("$.data[0].userId").doesNotExist())
				.andExpect(jsonPath("$.data[0].deletedAt").doesNotExist())
				.andExpect(jsonPath("$.data[0].fullName").doesNotExist())
				.andExpect(jsonPath("$.data[0].email").doesNotExist())
				.andExpect(jsonPath("$.data[0].phoneNumber").doesNotExist())
				.andExpect(jsonPath("$.data[0].address").doesNotExist());
	}

	@Test
	void returnsProfileDetailAndNotFoundContract() throws Exception {
		when(profileService.get(7L, 8L)).thenReturn(new ProfileDetailResponse(8L, "Default", "First", "Last",
				"Engineer", null, "Personality", "Summary", false, 3L, null, null));

		mockMvc.perform(get("/api/profiles/8").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileName").value("Default"))
				.andExpect(jsonPath("$.data.firstName").value("First"))
				.andExpect(jsonPath("$.data.lastName").value("Last"))
				.andExpect(jsonPath("$.data.personality").value("Personality"))
				.andExpect(jsonPath("$.data.technicalSummary").value("Summary"))
				.andExpect(jsonPath("$.data.version").value(3)).andExpect(jsonPath("$.data.userId").doesNotExist())
				.andExpect(jsonPath("$.data.deletedAt").doesNotExist())
				.andExpect(jsonPath("$.data.fullName").doesNotExist())
				.andExpect(jsonPath("$.data.email").doesNotExist())
				.andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
				.andExpect(jsonPath("$.data.address").doesNotExist());

		when(profileService.get(7L, 9L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND,
				"Profile not found"));
		mockMvc.perform(get("/api/profiles/9").with(principal())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
	}

	@Test
	void updatesProfileForAuthenticatedOwner() throws Exception {
		when(profileService.update(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(8L),
				org.mockito.ArgumentMatchers.any())).thenReturn(new ProfileDetailResponse(8L, "Updated", "First", "Last",
					"Developer", null, "Friendly", "Technical summary", false, 1L, null, null));

		mockMvc.perform(put("/api/profiles/8").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"profileName\":\"Updated\",\"firstName\":\"First\",\"lastName\":\"Last\","
						+ "\"jobTitle\":\"Developer\",\"yearsOfExperience\":0,\"personality\":\"Friendly\","
						+ "\"technicalSummary\":\"Technical summary\",\"version\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.profileName").value("Updated"))
				.andExpect(jsonPath("$.data.firstName").value("First"))
				.andExpect(jsonPath("$.data.version").value(1));
	}

	@Test
	void requiresAuthenticationForProfileUpdate() throws Exception {
		mockMvc.perform(put("/api/profiles/8").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deletesProfileForAuthenticatedOwner() throws Exception {
		mockMvc.perform(delete("/api/profiles/8").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").doesNotExist());
		org.mockito.Mockito.verify(profileService).delete(7L, 8L);
	}

	@Test
	void rejectsUnauthenticatedProfileDeletion() throws Exception {
		mockMvc.perform(delete("/api/profiles/8")).andExpect(status().isUnauthorized());
	}

	@Test
	void returnsDeletionBusinessErrors() throws Exception {
		doThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found"))
				.when(profileService).delete(7L, 8L);
		mockMvc.perform(delete("/api/profiles/8").with(principal())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));

		doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_LAST_ACTIVE_CANNOT_DELETE,
				"The last active Profile cannot be deleted")).when(profileService).delete(7L, 9L);
		mockMvc.perform(delete("/api/profiles/9").with(principal())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_LAST_ACTIVE_CANNOT_DELETE"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER), null, List.of()));
	}

	private String profileJson(String firstName, String lastName, String jobTitle, String personality,
			String technicalSummary) {
		return profileJson(firstName, lastName, jobTitle, personality, technicalSummary, "3.5");
	}

	private String profileJson(String firstName, String lastName, String jobTitle, String personality,
			String technicalSummary, String yearsOfExperience) {
		return "{\"profileName\":\"Default\",\"firstName\":\"" + firstName + "\",\"lastName\":\""
				+ lastName + "\",\"jobTitle\":\"" + jobTitle + "\",\"yearsOfExperience\":"
				+ yearsOfExperience + ",\"personality\":\"" + personality + "\",\"technicalSummary\":\""
				+ technicalSummary + "\"}";
	}

	private String updateJson(String firstName, String lastName, String jobTitle, String personality,
			String technicalSummary) {
		return profileJson(firstName, lastName, jobTitle, personality, technicalSummary).replace("}", ",\"version\":0}");
	}
}
