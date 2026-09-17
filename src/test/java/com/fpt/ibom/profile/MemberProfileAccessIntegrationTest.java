package com.fpt.ibom.profile;

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
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
class MemberProfileAccessIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Test
	void managerCanListActiveProfilesForInactiveMemberAndEmptyMember() throws Exception {
		UserAccount inactiveMember = saveUser(UserRole.MEMBER, UserStatus.INACTIVE);
		Profile active = saveProfile(inactiveMember, "Active");
		Profile deleted = saveProfile(inactiveMember, "Deleted");
		deleted.softDelete(Instant.now());
		profileRepository.saveAndFlush(deleted);

		mockMvc.perform(get("/api/members/{memberId}/profiles", inactiveMember.getId())
				.with(authentication(principal(30L, UserRole.MANAGER))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(active.getId()))
				.andExpect(jsonPath("$.data[0].profileName").value("Active"))
				.andExpect(jsonPath("$.data[0].userId").doesNotExist())
				.andExpect(jsonPath("$.data[0].deletedAt").doesNotExist());

		UserAccount emptyMember = saveUser(UserRole.MEMBER, UserStatus.INACTIVE);
		mockMvc.perform(get("/api/members/{memberId}/profiles", emptyMember.getId())
				.with(authentication(principal(30L, UserRole.MANAGER))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void rejectsMissingNonMemberAndMemberListingCallers() throws Exception {
		UserAccount member = saveUser(UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);

		mockMvc.perform(get("/api/members/999999999/profiles").with(authentication(principal(30L, UserRole.ADMIN))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
		mockMvc.perform(get("/api/members/{memberId}/profiles", manager.getId())
				.with(authentication(principal(30L, UserRole.ADMIN))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
		mockMvc.perform(get("/api/members/{memberId}/profiles", member.getId())
				.with(authentication(principal(30L, UserRole.MEMBER))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/members/{memberId}/profiles", member.getId()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsOwnerAndManagerAdminMemberDetailReadsAndHidesDeniedProfiles() throws Exception {
		UserAccount member = saveUser(UserRole.MEMBER, UserStatus.INACTIVE);
		Profile memberProfile = saveProfile(member, "Member");
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		Profile managerProfile = saveProfile(manager, "Manager");
		Profile deleted = saveProfile(member, "Deleted");
		deleted.softDelete(Instant.now());
		profileRepository.saveAndFlush(deleted);

		mockMvc.perform(get("/api/profiles/{profileId}", memberProfile.getId())
				.with(authentication(principal(30L, UserRole.ADMIN))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileName").value("Member"));
		mockMvc.perform(get("/api/profiles/{profileId}", memberProfile.getId())
				.with(authentication(principal(member.getId(), UserRole.MEMBER))))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/profiles/{profileId}", managerProfile.getId())
				.with(authentication(principal(30L, UserRole.ADMIN))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
		mockMvc.perform(get("/api/profiles/{profileId}", deleted.getId())
				.with(authentication(principal(30L, UserRole.ADMIN))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
	}

	private UserAccount saveUser(UserRole role, UserStatus status) {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"user-" + UUID.randomUUID(), "hash", role, status));
	}

	private Profile saveProfile(UserAccount user, String name) {
		Profile profile = new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
		return profileRepository.saveAndFlush(profile);
	}

	private UsernamePasswordAuthenticationToken principal(Long userId, UserRole role) {
		return new UsernamePasswordAuthenticationToken(new UserPrincipal(userId, "user@example.com", "user", role),
				null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name())));
	}
}
