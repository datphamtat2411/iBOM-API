package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
	void createsAtLeastSevenProfilesThroughHttp() throws Exception {
		UserAccount user = saveUser();

		for (int index = 1; index <= 7; index++) {
			String profileName = "Profile " + index;
			mockMvc.perform(post("/api/profiles").with(authentication(userPrincipal(user)))
					.contentType(MediaType.APPLICATION_JSON).content(requestJson(profileName)))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileName").value(profileName));
		}

		mockMvc.perform(get("/api/profiles/me").with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(7));
	}

	@Test
	void createsProfilesWithOptionalFieldsAbsentNullBlankAndPartiallyPopulated() throws Exception {
		UserAccount user = saveUser();

		Profile absent = createPersistedProfile(user, requestJsonWithoutOptionalFields("Absent"));
		assertNull(absent.getPersonality());
		assertNull(absent.getTechnicalSummary());

		Profile nulls = createPersistedProfile(user, requestJson("Nulls", null, null));
		assertNull(nulls.getPersonality());
		assertNull(nulls.getTechnicalSummary());

		Profile blanks = createPersistedProfile(user, requestJson("Blanks", "   ", ""));
		assertNull(blanks.getPersonality());
		assertNull(blanks.getTechnicalSummary());

		Profile oneField = createPersistedProfile(user, requestJson("OneField", "Personality", null));
		assertEquals("Personality", oneField.getPersonality());
		assertNull(oneField.getTechnicalSummary());
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
	void listsOnlyAuthenticatedOwnersActiveProfilesThroughHttp() throws Exception {
		UserAccount owner = saveUser();
		UserAccount other = saveUser();
		Profile active = saveProfile(owner, "Active");
		Profile deleted = saveProfile(owner, "Deleted");
		saveProfile(other, "Foreign");
		profileService.delete(owner.getId(), deleted.getId());

		mockMvc.perform(get("/api/profiles/me").with(authentication(userPrincipal(owner))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value(active.getId()))
				.andExpect(jsonPath("$.data[0].profileName").value("Active"));
	}

	@Test
	void keepsSurvivingProfileReadableAfterAnotherProfileIsDeletedThroughHttp() throws Exception {
		UserAccount user = saveUser();
		Profile deleted = saveProfile(user, "Deleted");
		Profile surviving = saveProfile(user, "Surviving");

		mockMvc.perform(delete("/api/profiles/{id}", deleted.getId()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/profiles/{id}", surviving.getId()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(surviving.getId()))
				.andExpect(jsonPath("$.data.profileName").value("Surviving"));
	}

	@Test
	void resolvesExplicitProfileIdsIndependentlyAcrossSequentialHttpReads() throws Exception {
		UserAccount user = saveUser();
		Profile first = saveProfile(user, "First");
		Profile second = saveProfile(user, "Second");

		mockMvc.perform(get("/api/profiles/{id}", first.getId()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileName").value("First"));
		mockMvc.perform(get("/api/profiles/{id}", second.getId()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileName").value("Second"));
		mockMvc.perform(get("/api/profiles/{id}", first.getId()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileName").value("First"));
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
	void updatesThroughHttpCanClearEitherOrBothOptionalFields() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Original");

		mockMvc.perform(put("/api/profiles/{id}", profile.getId()).with(authentication(userPrincipal(user)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateJson("PersonalityCleared", null, "Kept summary", 0)))
				.andExpect(status().isOk());
		Profile personalityCleared = profileRepository.findById(profile.getId()).orElseThrow();
		assertNull(personalityCleared.getPersonality());
		assertEquals("Kept summary", personalityCleared.getTechnicalSummary());

		mockMvc.perform(put("/api/profiles/{id}", profile.getId()).with(authentication(userPrincipal(user)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateJson("SummaryCleared", "Kept personality", null, 1)))
				.andExpect(status().isOk());
		Profile summaryCleared = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals("Kept personality", summaryCleared.getPersonality());
		assertNull(summaryCleared.getTechnicalSummary());

		mockMvc.perform(put("/api/profiles/{id}", profile.getId()).with(authentication(userPrincipal(user)))
				.contentType(MediaType.APPLICATION_JSON).content(updateJson("BothCleared", "   ", "", 2)))
				.andExpect(status().isOk());
		Profile bothCleared = profileRepository.findById(profile.getId()).orElseThrow();
		assertNull(bothCleared.getPersonality());
		assertNull(bothCleared.getTechnicalSummary());
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
		return updateJson(name, "New personality", "New summary", version);
	}

	private String requestJson(String name) {
		return requestJson(name, "Personality", "Summary");
	}

	private String requestJsonWithoutOptionalFields(String name) {
		return "{\"profileName\":\"" + name + "\",\"firstName\":\"First\",\"lastName\":\"Last\"," 
				+ "\"jobTitle\":\"Engineer\",\"yearsOfExperience\":2.5}";
	}

	private String requestJson(String name, String personality, String technicalSummary) {
		return "{\"profileName\":\"" + name + "\",\"firstName\":\"First\",\"lastName\":\"Last\"," 
				+ "\"jobTitle\":\"Engineer\",\"yearsOfExperience\":2.5,\"personality\":"
				+ jsonValue(personality) + ",\"technicalSummary\":" + jsonValue(technicalSummary) + "}";
	}

	private String updateJson(String name, String personality, String technicalSummary, long version) {
		return "{\"profileName\":\"" + name + "\",\"firstName\":\"NewFirst\",\"lastName\":\"NewLast\"," 
				+ "\"jobTitle\":\"Developer\",\"yearsOfExperience\":0,\"personality\":"
				+ jsonValue(personality) + ",\"technicalSummary\":" + jsonValue(technicalSummary)
				+ ",\"version\":" + version + "}";
	}

	private String jsonValue(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private Profile createPersistedProfile(UserAccount user, String request) throws Exception {
		String response = mockMvc.perform(post("/api/profiles").with(authentication(userPrincipal(user)))
				.contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		Long profileId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.data.id")).longValue();
		return profileRepository.findById(profileId).orElseThrow();
	}
}
