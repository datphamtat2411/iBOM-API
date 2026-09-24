package com.fpt.ibom.member;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MemberSearchIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private ProfileSkillRepository profileSkillRepository;
	@Autowired
	private ProfileLanguageRepository profileLanguageRepository;
	@Autowired
	private SkillCategoryRepository skillCategoryRepository;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private SeniorityRepository seniorityRepository;
	@Autowired
	private LanguageRepository languageRepository;

	@Test
	void combinesAllProfilePredicatesOnOneActiveProfileWithEvidenceOrderingAndRecovery() throws Exception {
		String marker = UUID.randomUUID().toString();
		SkillCategory category = skillCategoryRepository.saveAndFlush(new SkillCategory("CAT-" + marker, "Category"));
		Skill skill = skillRepository.saveAndFlush(new Skill("Skill-" + marker, category));
		Seniority seniority = seniorityRepository.saveAndFlush(new Seniority("Mid-" + marker,
				new BigDecimal("1.00"), new BigDecimal("3.00")));
		Language language = languageRepository.saveAndFlush(new Language("Language " + marker));

		UserAccount matching = saveUser("a-match-" + marker, UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount inactive = saveUser("b-inactive-" + marker, UserRole.MEMBER, UserStatus.INACTIVE);
		UserAccount split = saveUser("c-split-" + marker, UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount deletedOnly = saveUser("d-deleted-" + marker, UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount manager = saveUser("manager-" + marker, UserRole.MANAGER, UserStatus.ACTIVE);

		Profile matchingProfile = saveProfile(matching, "Matching");
		addPair(matchingProfile, skill, language, new BigDecimal("2.00"), LanguageLevel.ADVANCED);
		Profile partialProfile = saveProfile(matching, "Partial");
		profileSkillRepository.saveAndFlush(new com.fpt.ibom.profile.entity.ProfileSkill(partialProfile, skill,
				new BigDecimal("2.00"), null));

		Profile inactiveProfile = saveProfile(inactive, "Inactive");
		addPair(inactiveProfile, skill, language, new BigDecimal("2.00"), LanguageLevel.ADVANCED);

		Profile splitSkillProfile = saveProfile(split, "Split Skill");
		profileSkillRepository.saveAndFlush(new com.fpt.ibom.profile.entity.ProfileSkill(splitSkillProfile, skill,
				new BigDecimal("2.00"), null));
		Profile splitLanguageProfile = saveProfile(split, "Split Language");
		profileLanguageRepository.saveAndFlush(new com.fpt.ibom.profile.entity.ProfileLanguage(splitLanguageProfile,
				language, LanguageLevel.ADVANCED));

		Profile deletedProfile = saveProfile(deletedOnly, "Deleted");
		addPair(deletedProfile, skill, language, new BigDecimal("2.00"), LanguageLevel.ADVANCED);
		deletedProfile.softDelete(Instant.now());
		profileRepository.saveAndFlush(deletedProfile);

		String request = """
				{"skills":[{"skillId":%d,"seniorityId":%d}],
				"languages":[{"languageId":%d,"level":"ADVANCED"}],"page":9,"size":1}
				""".formatted(skill.getId(), seniority.getId(), language.getId());
		mockMvc.perform(post("/api/members/search").contentType("application/json").content(request)
				.with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.totalPages").value(2)).andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.content[0].username").value(inactive.getUsername()))
				.andExpect(jsonPath("$.data.content[0].status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.content[0].activeProfileCount").value(1))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].profileName")
						.value(inactiveProfile.getProfileName()));

			String firstPageRequest = request.replace("\"page\":9", "\"page\":0");
			mockMvc.perform(post("/api/members/search").contentType("application/json").content(firstPageRequest)
					.with(authentication(userPrincipal(manager))))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(matching.getId()))
					.andExpect(jsonPath("$.data.content[0].matchingProfiles.length()").value(1))
					.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].id").value(matchingProfile.getId()))
					.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].profileName")
							.value(matchingProfile.getProfileName()));
	}

	private UserAccount saveUser(String username, UserRole role, UserStatus status) {
		return userRepository.saveAndFlush(new UserAccount(username + "@example.com", username, "hash", role, status));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name + "-" + UUID.randomUUID(), "First", "Last",
				"Engineer", BigDecimal.ONE, "Personality", "Summary"));
	}

	private void addPair(Profile profile, Skill skill, Language language, BigDecimal experienceYears,
			LanguageLevel level) {
		profileSkillRepository.saveAndFlush(new com.fpt.ibom.profile.entity.ProfileSkill(profile, skill, experienceYears, null));
		profileLanguageRepository.saveAndFlush(new com.fpt.ibom.profile.entity.ProfileLanguage(profile, language, level));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}
}
