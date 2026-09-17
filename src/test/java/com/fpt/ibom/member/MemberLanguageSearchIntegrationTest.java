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
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
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
class MemberLanguageSearchIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private ProfileLanguageRepository profileLanguageRepository;
	@Autowired
	private LanguageRepository languageRepository;

	@Test
	void searchesExactPairsOnOneActiveProfileWithMemberPaginationAndEvidence() throws Exception {
		String marker = UUID.randomUUID().toString();
		Language english = languageRepository.saveAndFlush(new Language("English " + marker));
		Language vietnamese = languageRepository.saveAndFlush(new Language("Vietnamese " + marker));
		UserAccount matching = saveUser("beta-" + marker, UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount inactive = saveUser("alpha-" + marker, UserRole.MEMBER, UserStatus.INACTIVE);
		UserAccount split = saveUser("split-" + marker, UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount manager = saveUser("manager-" + marker, UserRole.MANAGER, UserStatus.ACTIVE);

		Profile first = saveProfile(matching, "First");
		Profile second = saveProfile(matching, "Second");
		Profile deleted = saveProfile(matching, "Deleted");
		addPair(first, english, vietnamese, LanguageLevel.ADVANCED, LanguageLevel.NATIVE);
		addPair(second, english, vietnamese, LanguageLevel.ADVANCED, LanguageLevel.NATIVE);
		addPair(deleted, english, vietnamese, LanguageLevel.ADVANCED, LanguageLevel.NATIVE);
		deleted.softDelete(Instant.now());
		profileRepository.saveAndFlush(deleted);

		Profile inactiveProfile = saveProfile(inactive, "Inactive");
		addPair(inactiveProfile, english, vietnamese, LanguageLevel.ADVANCED, LanguageLevel.NATIVE);
		Profile splitEnglish = saveProfile(split, "Split English");
		Profile splitVietnamese = saveProfile(split, "Split Vietnamese");
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(splitEnglish, english, LanguageLevel.ADVANCED));
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(splitVietnamese, vietnamese, LanguageLevel.NATIVE));

		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", english.getId().toString(), vietnamese.getId().toString())
				.param("levels", "ADVANCED", "NATIVE").param("size", "1")
				.with(authentication(userPrincipal(manager)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].username").value(matching.getUsername()))
				.andExpect(jsonPath("$.data.content[0].activeProfileCount").value(2))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles.length()").value(2))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].matchingLanguages.length()").value(2))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].matchingLanguages[0].languageName").value("Vietnamese " + marker))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].matchingLanguages[0].level").value("NATIVE"));

		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", english.getId().toString(), vietnamese.getId().toString())
				.param("levels", "ADVANCED", "NATIVE").param("status", "INACTIVE")
				.with(authentication(userPrincipal(manager)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].username").value(inactive.getUsername()));

		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", english.getId().toString(), vietnamese.getId().toString())
				.param("levels", "NATIVE", "ADVANCED").with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
	}

	private UserAccount saveUser(String username, UserRole role, UserStatus status) {
		return userRepository.saveAndFlush(new UserAccount(username + "@example.com", username, "hash", role, status));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name + "-" + UUID.randomUUID(), "First", "Last", "Engineer",
				BigDecimal.ONE, "Personality", "Summary"));
	}

	private void addPair(Profile profile, Language english, Language vietnamese, LanguageLevel englishLevel,
			LanguageLevel vietnameseLevel) {
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, english, englishLevel));
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, vietnamese, vietnameseLevel));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}
}
