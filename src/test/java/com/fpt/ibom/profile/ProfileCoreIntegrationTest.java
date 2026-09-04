package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileRequest;
import com.fpt.ibom.profile.dto.ProfileUpdateRequest;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileCoreIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProfileService profileService;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userRepository;

	@Test
	void createsThroughHttpAndReadsPersistedCanonicalProfile() throws Exception {
		UserAccount user = saveUser();
		String response = mockMvc.perform(post("/api/profiles").with(authentication(userPrincipal(user)))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson("Created")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201))
				.andExpect(jsonPath("$.data.profileName").value("Created"))
				.andExpect(jsonPath("$.data.hasPreviewed").value(false))
				.andExpect(jsonPath("$.data.version").value(0))
				.andExpect(jsonPath("$.data.fullName").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		Long profileId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.data.id")).longValue();

		mockMvc.perform(get("/api/profiles/{id}", profileId).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileName").value("Created"))
				.andExpect(jsonPath("$.data.firstName").value("First"))
				.andExpect(jsonPath("$.data.yearsOfExperience").value(2.5))
				.andExpect(jsonPath("$.data.technicalSummary").value("Summary"));
		assertEquals("Created", profileRepository.findById(profileId).orElseThrow().getProfileName());
	}

	@Test
	void listsOnlyOwnedActiveProfilesAndSupportsZeroProfiles() {
		UserAccount owner = saveUser();
		UserAccount other = saveUser();
		assertEquals(List.of(), profileService.list(owner.getId()));
		Profile first = saveProfile(owner, "First");
		saveProfile(owner, "Second");
		saveProfile(other, "Foreign");
		profileService.delete(owner.getId(), first.getId());

		assertEquals(List.of("Second"), profileService.list(owner.getId()).stream()
				.map(summary -> summary.profileName()).toList());
	}

	@Test
	void updatesPersistCanonicalFieldsAndRejectsStaleVersion() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Original");
		var updated = profileService.update(user.getId(), profile.getId(), updateRequest("Updated", 0L));

		assertEquals(1L, updated.version());
		assertFalse(updated.hasPreviewed());
		ApiException conflict = assertThrows(ApiException.class, () -> profileService.update(user.getId(), profile.getId(),
				new ProfileUpdateRequest("Stale", "Other", "Other", "Other", BigDecimal.ONE, "Other", "Other", 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
		assertEquals("Updated", profileRepository.findById(profile.getId()).orElseThrow().getProfileName());
	}

	@Test
	void updatesThroughHttpAndReturnsUpdatedPersistedValues() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Original");

		mockMvc.perform(put("/api/profiles/{id}", profile.getId()).with(authentication(userPrincipal(user)))
				.contentType(MediaType.APPLICATION_JSON).content(updateJson("Updated", 0)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.profileName").value("Updated"))
				.andExpect(jsonPath("$.data.firstName").value("NewFirst"))
				.andExpect(jsonPath("$.data.jobTitle").value("Developer"))
				.andExpect(jsonPath("$.data.hasPreviewed").value(false))
				.andExpect(jsonPath("$.data.version").value(1));

		mockMvc.perform(get("/api/profiles/{id}", profile.getId()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileName").value("Updated"))
				.andExpect(jsonPath("$.data.technicalSummary").value("New summary"));
	}

	@Test
	void rejectsCaseInsensitiveTrimmedDuplicateNames() {
		UserAccount user = saveUser();
		saveProfile(user, "Existing");
		ApiException conflict = assertThrows(ApiException.class, () -> profileService.create(user.getId(),
				new ProfileRequest(" existing ", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality", "Summary")));
		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, conflict.getErrorCode());
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com", "member" + UUID.randomUUID(),
				"hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer", new BigDecimal("2.5"),
				"Personality", "Summary"));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null, List.of());
	}

	private ProfileUpdateRequest updateRequest(String name, long version) {
		return new ProfileUpdateRequest(name, "NewFirst", "NewLast", "Developer", BigDecimal.ZERO,
				"New personality", "New summary", version);
	}

	private String updateJson(String name, long version) {
		return "{\"profileName\":\"" + name + "\",\"firstName\":\"NewFirst\",\"lastName\":\"NewLast\","
				+ "\"jobTitle\":\"Developer\",\"yearsOfExperience\":0,\"personality\":\"New personality\","
				+ "\"technicalSummary\":\"New summary\",\"version\":" + version + "}";
	}

	private String requestJson(String name) {
		return "{\"profileName\":\"" + name + "\",\"firstName\":\"First\",\"lastName\":\"Last\","
				+ "\"jobTitle\":\"Engineer\",\"yearsOfExperience\":2.5,\"personality\":\"Personality\","
				+ "\"technicalSummary\":\"Summary\"}";
	}
}
