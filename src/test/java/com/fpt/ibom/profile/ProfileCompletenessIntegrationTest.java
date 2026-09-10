package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileCompletenessIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserAccountRepository users;
	@Autowired
	private ProfileRepository profiles;
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

	@Test
	void calculatesPersistedCompletenessWithoutChangingProfileState() throws Exception {
		UserAccount owner = saveUser();
		Profile profile = profiles.saveAndFlush(new Profile(owner, "Complete", "First", "Last", "Engineer",
				BigDecimal.ZERO, "Personality", "Summary"));
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		profile = profiles.saveAndFlush(profile);
		long version = profile.getVersion();
		Instant createdAt = profile.getCreatedAt();
		Instant updatedAt = profile.getUpdatedAt();

		String suffix = UUID.randomUUID().toString().replace("-", "");
		Language language = languages.saveAndFlush(new Language("Completeness Language " + suffix));
		SkillCategory category = skillCategories.saveAndFlush(new SkillCategory("CMP" + suffix, "Completeness " + suffix));
		Skill skill = skills.saveAndFlush(new Skill("Completeness Skill " + suffix, category));
		educations.saveAndFlush(new Education(profile, "School", "Degree", null, LocalDate.of(2020, 1, 1), null,
				EducationStatus.ONGOING));
		profileLanguages.saveAndFlush(new ProfileLanguage(profile, language, LanguageLevel.NATIVE));
		certificates.saveAndFlush(new Certificate(profile, "AWS", LocalDate.of(2025, 1, 1)));
		projects.saveAndFlush(new Project(profile, "Project", "Description", LocalDate.of(2020, 1, 1), null,
				ProjectStatus.ONGOING, "Engineer", 1, "Responsibilities", "Java", null));
		profileSkills.saveAndFlush(new ProfileSkill(profile, skill, BigDecimal.ZERO, null));

		mockMvc.perform(get("/api/profiles/{id}/completeness", profile.getId())
				.with(authentication(userPrincipal(owner)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.percentage").value(100))
				.andExpect(jsonPath("$.data.completed").value(true))
				.andExpect(jsonPath("$.data.sections[0].validFieldCount").value(6))
				.andExpect(jsonPath("$.data.sections[1].hasQualifyingRecord").value(true))
				.andExpect(jsonPath("$.data.sections[5].hasQualifyingRecord").value(true));

		Profile unchanged = profiles.findById(profile.getId()).orElseThrow();
		assertEquals(version, unchanged.getVersion());
		assertTrue(unchanged.isHasPreviewed());
		assertEquals(createdAt, unchanged.getCreatedAt());
		assertEquals(updatedAt, unchanged.getUpdatedAt());
	}

	@Test
	void hidesForeignAndSoftDeletedProfiles() throws Exception {
		UserAccount owner = saveUser();
		UserAccount foreign = saveUser();
		Profile foreignProfile = profiles.saveAndFlush(new Profile(foreign, "Foreign", "First", "Last", "Engineer",
				BigDecimal.ZERO, null, null));
		Profile deleted = profiles.saveAndFlush(new Profile(owner, "Deleted", "First", "Last", "Engineer",
				BigDecimal.ZERO, null, null));
		deleted.softDelete(Instant.now());
		profiles.saveAndFlush(deleted);

		mockMvc.perform(get("/api/profiles/{id}/completeness", foreignProfile.getId())
				.with(authentication(userPrincipal(owner)))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
		mockMvc.perform(get("/api/profiles/{id}/completeness", deleted.getId())
				.with(authentication(userPrincipal(owner)))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
	}

	private UserAccount saveUser() {
		return users.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com", "member" + UUID.randomUUID(),
				"hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null, List.of());
	}
}
