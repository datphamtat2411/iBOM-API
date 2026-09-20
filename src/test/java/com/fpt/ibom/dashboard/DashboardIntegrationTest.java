package com.fpt.ibom.dashboard;

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

@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProfileRepository profiles;

	@Autowired
	private UserAccountRepository users;

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

	private UserAccount saveUser(UserRole role) {
		return users.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com", "user-" + UUID.randomUUID(),
				"hash", role, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		Profile profile = new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
		return profiles.saveAndFlush(profile);
	}

	private UsernamePasswordAuthenticationToken principal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(),
				user.getRole()), null, List.of());
	}
}
