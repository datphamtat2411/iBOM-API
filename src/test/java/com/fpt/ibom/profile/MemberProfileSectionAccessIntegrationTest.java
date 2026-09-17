package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class MemberProfileSectionAccessIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private LanguageRepository languageRepository;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private SkillCategoryRepository skillCategoryRepository;
	@Autowired
	private EducationRepository educationRepository;
	@Autowired
	private ProfileLanguageRepository profileLanguageRepository;
	@Autowired
	private CertificateRepository certificateRepository;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private ProfileSkillRepository profileSkillRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void managerAndAdminCanListAndMutateEverySectionOfMemberProfiles() throws Exception {
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		UserAccount admin = saveUser(UserRole.ADMIN, UserStatus.ACTIVE);
		Profile managerProfile = saveProfile(saveUser(UserRole.MEMBER, UserStatus.ACTIVE), "Manager Target");
		Profile adminProfile = saveProfile(saveUser(UserRole.MEMBER, UserStatus.ACTIVE), "Admin Target");
		Language language = findLanguage("English");
		Skill skill = saveSkill("access-skill-" + UUID.randomUUID());

		mutateAllSections(manager, managerProfile, language, skill, "manager");
		mutateAllSections(admin, adminProfile, language, skill, "admin");

		assertAllSectionsListed(manager, managerProfile);
		assertAllSectionsListed(admin, adminProfile);

		assertProfileState(managerProfile, 5L, false);
		assertProfileState(adminProfile, 5L, false);

		markPreviewed(managerProfile);
		markPreviewed(adminProfile);
		assertAllSectionsListed(manager, managerProfile);
		assertAllSectionsListed(admin, adminProfile);
		assertProfileState(managerProfile, 5L, true);
		assertProfileState(adminProfile, 5L, true);
	}

	@Test
	void managerCanManageProfileOwnedByInactiveMember() throws Exception {
		UserAccount inactiveMember = saveUser(UserRole.MEMBER, UserStatus.INACTIVE);
		Profile profile = saveProfile(inactiveMember, "Inactive Member Target");
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);

		mockMvc.perform(get("/api/profiles/{id}/educations", profile.getId()).with(auth(manager)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
		mockMvc.perform(post("/api/profiles/{id}/educations", profile.getId()).with(auth(manager))
				.contentType(MediaType.APPLICATION_JSON).content(educationJson("Inactive Education", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1));
	}

	@Test
	void rejectsUnauthorizedMissingAndSoftDeletedParentProfilesAsNotFound() throws Exception {
		UserAccount member = saveUser(UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount otherMember = saveUser(UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		UserAccount admin = saveUser(UserRole.ADMIN, UserStatus.ACTIVE);
		Profile memberProfile = saveProfile(member, "Member Target");
		Profile otherMemberProfile = saveProfile(otherMember, "Other Member Target");
		Profile managerProfile = saveProfile(manager, "Manager Owned Target");
		Profile adminProfile = saveProfile(admin, "Admin Owned Target");

		assertProfileNotFound(otherMember, memberProfile.getId());
		assertProfileNotFound(admin, managerProfile.getId());
		assertProfileNotFound(manager, adminProfile.getId());
		assertProfileNotFound(manager, Long.MAX_VALUE);

		Profile deleted = profileRepository.findById(memberProfile.getId()).orElseThrow();
		deleted.softDelete(Instant.now());
		profileRepository.saveAndFlush(deleted);
		assertProfileNotFound(manager, deleted.getId());
	}

	@Test
	void rejectsForeignChildIdsWithExistingSectionNotFoundErrors() throws Exception {
		UserAccount owner = saveUser(UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount targetOwner = saveUser(UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		Profile ownerProfile = saveProfile(owner, "Foreign Child Source");
		Profile targetProfile = saveProfile(targetOwner, "Foreign Child Target");
		Language language = findLanguage("English");
		Skill skill = saveSkill("foreign-skill-" + UUID.randomUUID());

		Education education = educationRepository.saveAndFlush(new Education(ownerProfile, "School", "Degree", null,
				LocalDate.of(2020, 1, 1), null, EducationStatus.ONGOING));
		ProfileLanguage profileLanguage = profileLanguageRepository
				.saveAndFlush(new ProfileLanguage(ownerProfile, language, LanguageLevel.NATIVE));
		Certificate certificate = certificateRepository
				.saveAndFlush(new Certificate(ownerProfile, "Certificate", LocalDate.of(2024, 1, 1)));
		Project project = projectRepository.saveAndFlush(new Project(ownerProfile, "Project", "Description",
				LocalDate.of(2020, 1, 1), null, ProjectStatus.ONGOING, "Engineer", 1, null, null, null));
		ProfileSkill profileSkill = profileSkillRepository
				.saveAndFlush(new ProfileSkill(ownerProfile, skill, BigDecimal.ONE, null));

		assertChildNotFound(manager, targetProfile, "/educations/" + education.getId(), "EDUCATION_NOT_FOUND");
		assertChildNotFound(manager, targetProfile, "/languages/" + profileLanguage.getId(), "PROFILE_LANGUAGE_NOT_FOUND");
		assertChildNotFound(manager, targetProfile, "/certificates/" + certificate.getId(), "CERTIFICATE_NOT_FOUND");
		assertChildNotFound(manager, targetProfile, "/projects/" + project.getId(), "PROJECT_NOT_FOUND");
		assertChildNotFound(manager, targetProfile, "/skills/" + profileSkill.getId(), "PROFILE_SKILL_NOT_FOUND");
	}

	@Test
	void staleManagerMutationDoesNotChangeChildProfileVersionOrPreview() throws Exception {
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		Profile profile = saveProfile(saveUser(UserRole.MEMBER, UserStatus.ACTIVE), "Stale Manager Target");
		markPreviewed(profile);

		mockMvc.perform(post("/api/profiles/{id}/educations", profile.getId()).with(auth(manager))
				.contentType(MediaType.APPLICATION_JSON).content(educationJson("Committed Education", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1));
		markPreviewed(profile);

		mockMvc.perform(post("/api/profiles/{id}/educations", profile.getId()).with(auth(manager))
				.contentType(MediaType.APPLICATION_JSON).content(educationJson("Stale Education", 0)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("PROFILE_VERSION_CONFLICT"));

		assertEquals(1, educationRepository.findByProfileIdOrderByIdAsc(profile.getId()).size());
		assertProfileState(profile, 1L, true);
	}

	private void mutateAllSections(UserAccount operator, Profile profile, Language language, Skill skill, String marker)
			throws Exception {
		mockMvc.perform(post("/api/profiles/{id}/educations", profile.getId()).with(auth(operator))
				.contentType(MediaType.APPLICATION_JSON).content(educationJson(marker + " Education", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1));
		mockMvc.perform(post("/api/profiles/{id}/languages", profile.getId()).with(auth(operator))
				.contentType(MediaType.APPLICATION_JSON).content(languageJson(language.getId(), 1)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(2));
		mockMvc.perform(post("/api/profiles/{id}/certificates", profile.getId()).with(auth(operator))
				.contentType(MediaType.APPLICATION_JSON).content(certificateJson(marker + " Certificate", 2)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(3));
		mockMvc.perform(post("/api/profiles/{id}/projects", profile.getId()).with(auth(operator))
				.contentType(MediaType.APPLICATION_JSON).content(projectJson(marker + " Project", 3)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(4));
		mockMvc.perform(post("/api/profiles/{id}/skills", profile.getId()).with(auth(operator))
				.contentType(MediaType.APPLICATION_JSON).content(skillJson(skill.getId(), 4)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(5));
	}

	private void assertAllSectionsListed(UserAccount operator, Profile profile) throws Exception {
		mockMvc.perform(get("/api/profiles/{id}/educations", profile.getId()).with(auth(operator)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").exists());
		mockMvc.perform(get("/api/profiles/{id}/languages", profile.getId()).with(auth(operator)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").exists());
		mockMvc.perform(get("/api/profiles/{id}/certificates", profile.getId()).with(auth(operator)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").exists());
		mockMvc.perform(get("/api/profiles/{id}/projects", profile.getId()).with(auth(operator)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").exists());
		mockMvc.perform(get("/api/profiles/{id}/skills", profile.getId()).with(auth(operator)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").exists());
	}

	private void assertProfileNotFound(UserAccount requester, Long profileId) throws Exception {
		for (String section : List.of("educations", "languages", "certificates", "projects", "skills")) {
			mockMvc.perform(get("/api/profiles/{id}/{section}", profileId, section).with(auth(requester)))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
		}
	}

	private void assertChildNotFound(UserAccount operator, Profile profile, String childPath, String errorCode) throws Exception {
		mockMvc.perform(delete("/api/profiles/" + profile.getId() + childPath).with(auth(operator))
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value(errorCode));
	}

	private void assertProfileState(Profile profile, long expectedVersion, boolean expectedPreviewed) {
		Profile reloaded = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(expectedVersion, reloaded.getVersion());
		assertEquals(expectedPreviewed, reloaded.isHasPreviewed());
	}

	private void markPreviewed(Profile profile) {
		jdbcTemplate.update("UPDATE profiles SET has_previewed = TRUE WHERE id = ?", profile.getId());
	}

	private UserAccount saveUser(UserRole role, UserStatus status) {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"section-access-" + UUID.randomUUID(), "hash", role, status));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary"));
	}

	private Language findLanguage(String name) {
		return languageRepository.findAll().stream().filter(language -> name.equals(language.getName())).findFirst().orElseThrow();
	}

	private Skill saveSkill(String name) {
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		return skillRepository.saveAndFlush(new Skill(name, category));
	}

	private RequestPostProcessor auth(UserAccount user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
		return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	private String educationJson(String name, long version) {
		return "{\"schoolName\":\"" + name + "\",\"degree\":\"Degree\",\"startDate\":\"2020-01-01\"," 
				+ "\"status\":\"ONGOING\",\"version\":" + version + "}";
	}

	private String languageJson(Long languageId, long version) {
		return "{\"languageId\":" + languageId + ",\"level\":\"NATIVE\",\"version\":" + version + "}";
	}

	private String certificateJson(String name, long version) {
		return "{\"certificateName\":\"" + name + "\",\"issueDate\":\"2024-01-01\",\"version\":" + version + "}";
	}

	private String projectJson(String name, long version) {
		return "{\"name\":\"" + name + "\",\"description\":\"Description\",\"startDate\":\"2020-01-01\","
				+ "\"status\":\"ONGOING\",\"position\":\"Engineer\",\"teamSize\":1,\"version\":" + version + "}";
	}

	private String skillJson(Long skillId, long version) {
		return "{\"skillId\":" + skillId + ",\"experienceYears\":1.25,\"version\":" + version + "}";
	}
}
