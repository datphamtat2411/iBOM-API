package com.fpt.ibom.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProfileRepository profiles;

	@Autowired
	private UserAccountRepository users;

	@Autowired
	private EducationRepository educations;

	@Autowired
	private ProfileLanguageRepository profileLanguages;

	@Autowired
	private CertificateRepository certificates;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private ProfileSkillRepository profileSkills;

	@Autowired
	private LanguageRepository languages;

	@Autowired
	private SkillCategoryRepository skillCategories;

	@Autowired
	private SkillRepository skills;

	@BeforeEach
	void clearProfilesAndChildRecords() {
		profileSkills.deleteAllInBatch();
		profileLanguages.deleteAllInBatch();
		certificates.deleteAllInBatch();
		projects.deleteAllInBatch();
		educations.deleteAllInBatch();
		profiles.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	@Test
	void returnsSelectedProfileCompletenessAndLatestExportAcrossActiveOwnedProfiles() throws Exception {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile selected = saveProfile(owner, "Selected");
		Profile other = saveProfile(owner, "Other");
		Profile deleted = saveProfile(owner, "Deleted");
		other.markExportedAt(Instant.parse("2026-02-03T04:05:06Z"));
		deleted.markExportedAt(Instant.parse("2026-03-03T04:05:06Z"));
		deleted.softDelete(Instant.parse("2026-01-01T00:00:00Z"));
		profiles.saveAndFlush(other);
		profiles.saveAndFlush(deleted);

		UserAccount foreignOwner = saveUser(UserRole.MEMBER);
		Profile foreign = saveProfile(foreignOwner, "Foreign");
		foreign.markExportedAt(Instant.parse("2026-04-03T04:05:06Z"));
		profiles.saveAndFlush(foreign);

		mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", selected.getId().toString())
				.with(authentication(principal(owner))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.selectedProfile.id").value(selected.getId()))
				.andExpect(jsonPath("$.data.selectedProfile.profileName").value("Selected"))
				.andExpect(jsonPath("$.data.completeness.percentage").value(20))
				.andExpect(jsonPath("$.data.completeness.sections[0].validFieldCount").value(6))
				.andExpect(jsonPath("$.data.completeness.sections[0].fieldCount").value(6))
				.andExpect(jsonPath("$.data.latestExportedAt").value("2026-02-03T04:05:06Z"));
	}

	@Test
	void excludesDeletedExportsAndHidesForeignAndDeletedSelectedProfiles() throws Exception {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile active = saveProfile(owner, "Active");
		Profile deleted = saveProfile(owner, "Deleted");
		deleted.markExportedAt(Instant.parse("2026-04-03T04:05:06Z"));
		deleted.softDelete(Instant.parse("2026-01-01T00:00:00Z"));
		profiles.saveAndFlush(deleted);
		UserAccount foreignOwner = saveUser(UserRole.MEMBER);
		Profile foreign = saveProfile(foreignOwner, "Foreign");

		mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", active.getId().toString())
				.with(authentication(principal(owner))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.latestExportedAt").value(org.hamcrest.Matchers.nullValue()));
		mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", foreign.getId().toString())
				.with(authentication(principal(owner))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
		mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", deleted.getId().toString())
				.with(authentication(principal(owner))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
	}

	@Test
	void countsOnlyActiveMemberProfilesAndOnlyCanonicallyCompletedProfiles() throws Exception {
		UserAccount member = saveUser(UserRole.MEMBER, UserStatus.ACTIVE);
		Profile completed = saveProfile(member, "Completed");
		addCompleteProfileData(completed);
		saveProfile(member, "Incomplete");
		Profile deleted = saveProfile(member, "Deleted");
		deleted.softDelete(Instant.parse("2026-01-01T00:00:00Z"));
		profiles.saveAndFlush(deleted);

		UserAccount inactiveMember = saveUser(UserRole.MEMBER, UserStatus.INACTIVE);
		saveProfile(inactiveMember, "Inactive");
		saveProfile(saveUser(UserRole.MANAGER, UserStatus.ACTIVE), "Manager");
		saveProfile(saveUser(UserRole.ADMIN, UserStatus.ACTIVE), "Admin");

		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		mockMvc.perform(get("/api/dashboard/manager-stats").with(authentication(principal(manager))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalProfiles").value(2))
				.andExpect(jsonPath("$.data.completedProfiles").value(1));
	}

	@Test
	void managerAnalyticsUsesOnlyPrimarySkillsFromEligibleProfiles() throws Exception {
		UserAccount member = saveUser(UserRole.MEMBER);
		Profile first = saveProfile(member, "First");
		Profile second = saveProfile(member, "Second");
		Profile deleted = saveProfile(member, "Deleted");
		deleted.softDelete(Instant.parse("2026-01-01T00:00:00Z"));
		profiles.saveAndFlush(deleted);

		Profile inactive = saveProfile(saveUser(UserRole.MEMBER, UserStatus.INACTIVE), "Inactive");
		Profile managerOwned = saveProfile(saveUser(UserRole.MANAGER), "Manager");
		Profile adminOwned = saveProfile(saveUser(UserRole.ADMIN), "Admin");

		String backendName = "Backend-" + UUID.randomUUID();
		String frontendName = "Frontend-" + UUID.randomUUID();
		SkillCategory backend = skillCategories.saveAndFlush(new SkillCategory("BACKEND-" + UUID.randomUUID(), backendName));
		SkillCategory frontend = skillCategories.saveAndFlush(new SkillCategory("FRONTEND-" + UUID.randomUUID(), frontendName));
		Skill java = skills.saveAndFlush(new Skill("Java-" + UUID.randomUUID(), backend));
		Skill python = skills.saveAndFlush(new Skill("Python-" + UUID.randomUUID(), frontend));
		Skill excluded = skills.saveAndFlush(new Skill("Excluded-" + UUID.randomUUID(), backend));
		profileSkills.saveAndFlush(new ProfileSkill(first, java, new BigDecimal("5.00"), LocalDate.of(2025, 1, 1)));
		profileSkills.saveAndFlush(new ProfileSkill(first, python, new BigDecimal("4.00"), LocalDate.of(2024, 1, 1)));
		profileSkills.saveAndFlush(new ProfileSkill(second, python, new BigDecimal("3.00"), LocalDate.of(2025, 1, 1)));
		profileSkills.saveAndFlush(new ProfileSkill(deleted, excluded, BigDecimal.ONE, LocalDate.of(2025, 1, 1)));
		profileSkills.saveAndFlush(new ProfileSkill(inactive, excluded, BigDecimal.ONE, LocalDate.of(2025, 1, 1)));
		profileSkills.saveAndFlush(new ProfileSkill(managerOwned, excluded, BigDecimal.ONE, LocalDate.of(2025, 1, 1)));
		profileSkills.saveAndFlush(new ProfileSkill(adminOwned, excluded, BigDecimal.ONE, LocalDate.of(2025, 1, 1)));

		UserAccount dashboardManager = saveUser(UserRole.MANAGER);
		mockMvc.perform(get("/api/dashboard/manager-stats").with(authentication(principal(dashboardManager))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalProfiles").value(2))
				.andExpect(jsonPath("$.data.completedProfiles").value(0))
				.andExpect(jsonPath("$.data.primarySkillDistribution.items.length()").value(2))
				.andExpect(jsonPath("$.data.primarySkillDistribution.items[0].skillName").value(java.getName()))
				.andExpect(jsonPath("$.data.primarySkillDistribution.items[0].profileCount").value(1))
				.andExpect(jsonPath("$.data.primarySkillDistribution.items[1].skillName").value(python.getName()))
				.andExpect(jsonPath("$.data.primarySkillDistribution.items[1].profileCount").value(1))
				.andExpect(jsonPath("$.data.primarySkillDistribution.otherProfileCount").value(0))
				.andExpect(jsonPath("$.data.skillCategoryDistribution.items.length()").value(2))
				.andExpect(jsonPath("$.data.skillCategoryDistribution.items[0].categoryName").value(backendName))
				.andExpect(jsonPath("$.data.skillCategoryDistribution.items[0].percentage").value(50))
				.andExpect(jsonPath("$.data.skillCategoryDistribution.items[1].categoryName").value(frontendName))
				.andExpect(jsonPath("$.data.skillCategoryDistribution.items[1].percentage").value(50))
				.andExpect(jsonPath("$.data.skillCategoryDistribution.otherProfileCount").value(0));
	}

	@Test
	void returnsZeroCountsWhenNoEligibleProfilesExist() throws Exception {
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);

		mockMvc.perform(get("/api/dashboard/manager-stats").with(authentication(principal(manager))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalProfiles").value(0))
				.andExpect(jsonPath("$.data.completedProfiles").value(0))
				.andExpect(jsonPath("$.data.primarySkillDistribution.items").isEmpty())
				.andExpect(jsonPath("$.data.primarySkillDistribution.otherProfileCount").value(0))
				.andExpect(jsonPath("$.data.skillCategoryDistribution.items").isEmpty())
				.andExpect(jsonPath("$.data.skillCategoryDistribution.otherProfileCount").value(0));
	}

	private void addCompleteProfileData(Profile profile) {
		educations.saveAndFlush(new Education(profile, "University", "Degree", "Field",
				LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 1), EducationStatus.COMPLETED));
		Language language = languages.saveAndFlush(new Language("English-" + UUID.randomUUID()));
		profileLanguages.saveAndFlush(new ProfileLanguage(profile, language, LanguageLevel.ADVANCED));
		certificates.saveAndFlush(new Certificate(profile, "Certification", LocalDate.of(2020, 1, 1)));
		projects.saveAndFlush(new Project(profile, "Project", "Description", LocalDate.of(2020, 1, 1),
				LocalDate.of(2021, 1, 1), ProjectStatus.COMPLETED, "Developer", 3, "Responsibilities",
				"Java", "Tools"));
		SkillCategory category = skillCategories.saveAndFlush(new SkillCategory("CAT-" + UUID.randomUUID(), "Category"));
		Skill skill = skills.saveAndFlush(new Skill("Java-" + UUID.randomUUID(), category));
		profileSkills.saveAndFlush(new ProfileSkill(profile, skill, BigDecimal.ONE, LocalDate.of(2021, 1, 1)));
	}

	private UserAccount saveUser(UserRole role) {
		return saveUser(role, UserStatus.ACTIVE);
	}

	private UserAccount saveUser(UserRole role, UserStatus status) {
		return users.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com", "user-" + UUID.randomUUID(),
				"hash", role, status));
	}

	private Profile saveProfile(UserAccount user, String name) {
		Profile profile = new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
		return profiles.saveAndFlush(profile);
	}

	private UsernamePasswordAuthenticationToken principal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(),
				user.getRole()), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}
}
