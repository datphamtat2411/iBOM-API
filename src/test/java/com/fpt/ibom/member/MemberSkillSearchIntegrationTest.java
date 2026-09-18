package com.fpt.ibom.member;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
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
class MemberSkillSearchIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private ProfileSkillRepository profileSkillRepository;

	@Autowired
	private SkillCategoryRepository skillCategoryRepository;

	@Autowired
	private SkillRepository skillRepository;

	@Autowired
	private SeniorityRepository seniorityRepository;

	@Test
	void matchesAllPairsWithinOneActiveProfileUsingExperienceRangesAndMemberPagination() throws Exception {
		SkillCategory category = skillCategoryRepository.saveAndFlush(new SkillCategory("CAT-" + UUID.randomUUID(), "Category"));
		Skill firstSkill = skillRepository.saveAndFlush(new Skill("First-" + UUID.randomUUID(), category));
		Skill secondSkill = skillRepository.saveAndFlush(new Skill("Second-" + UUID.randomUUID(), category));
		Seniority lower = seniorityRepository.saveAndFlush(new Seniority("Lower-" + UUID.randomUUID(),
				new BigDecimal("1.00"), new BigDecimal("3.00")));
		Seniority upper = seniorityRepository.saveAndFlush(new Seniority("Upper-" + UUID.randomUUID(),
				new BigDecimal("3.00"), null));

		UserAccount matching = saveMember("search-match-" + UUID.randomUUID(), UserStatus.ACTIVE);
		UserAccount split = saveMember("search-split-" + UUID.randomUUID(), UserStatus.ACTIVE);
		UserAccount inactive = saveMember("search-inactive-" + UUID.randomUUID(), UserStatus.INACTIVE);
		UserAccount manager = saveManager("search-manager-" + UUID.randomUUID(), UserStatus.ACTIVE);

		Profile matchingProfile = saveProfile(matching, "matching");
		profileSkillRepository.saveAndFlush(new ProfileSkill(matchingProfile, firstSkill, new BigDecimal("1.00"), null));
		profileSkillRepository.saveAndFlush(new ProfileSkill(matchingProfile, secondSkill, new BigDecimal("3.00"), null));

		Profile splitFirst = saveProfile(split, "split-first");
		Profile splitSecond = saveProfile(split, "split-second");
		profileSkillRepository.saveAndFlush(new ProfileSkill(splitFirst, firstSkill, new BigDecimal("2.00"), null));
		profileSkillRepository.saveAndFlush(new ProfileSkill(splitSecond, secondSkill, new BigDecimal("3.00"), null));

		Profile inactiveProfile = saveProfile(inactive, "inactive");
		profileSkillRepository.saveAndFlush(new ProfileSkill(inactiveProfile, firstSkill, new BigDecimal("2.00"), null));
		profileSkillRepository.saveAndFlush(new ProfileSkill(inactiveProfile, secondSkill, new BigDecimal("4.00"), null));

		Profile deleted = saveProfile(matching, "deleted");
		profileSkillRepository.saveAndFlush(new ProfileSkill(deleted, firstSkill, new BigDecimal("2.00"), null));
		profileSkillRepository.saveAndFlush(new ProfileSkill(deleted, secondSkill, new BigDecimal("4.00"), null));
		deleted.softDelete(Instant.now());
		profileRepository.saveAndFlush(deleted);

		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", firstSkill.getId().toString(), secondSkill.getId().toString())
				.param("seniorityIds", lower.getId().toString(), upper.getId().toString()).param("size", "1")
				.with(authentication(userPrincipal(manager)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].id").value(matching.getId()))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].id").value(matchingProfile.getId()))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].profileName")
						.value(matchingProfile.getProfileName()))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].firstName").value("First"))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].lastName").value("Last"))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].jobTitle").value("Engineer"))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].updatedAt").exists())
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].matches").doesNotExist());

		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", firstSkill.getId().toString(), secondSkill.getId().toString())
				.param("seniorityIds", lower.getId().toString(), upper.getId().toString()).param("status", "INACTIVE")
				.with(authentication(userPrincipal(manager)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].id").value(inactive.getId()));

		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "999999999")
				.param("seniorityIds", lower.getId().toString()).with(authentication(userPrincipal(manager))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
}

	private UserAccount saveMember(String username, UserStatus status) {
		return saveUser(username, UserRole.MEMBER, status);
	}

	private UserAccount saveManager(String username, UserStatus status) {
		return saveUser(username, UserRole.MANAGER, status);
	}

	private UserAccount saveUser(String username, UserRole role, UserStatus status) {
		return userAccountRepository.saveAndFlush(new UserAccount(username + "@example.com", username, "hash",
				role, status));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name + "-" + UUID.randomUUID(), "First", "Last",
				"Engineer", BigDecimal.ONE, "personality", "summary"));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}
}
