package com.fpt.ibom.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void listsAndFiltersAllAccountRolesWithSearchOrderingAndPagination() throws Exception {
		String marker = UUID.randomUUID().toString().replace("-", "");
		UserAccount beta = saveUser("user-beta-" + marker, UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount alpha = saveUser("user-alpha-" + marker, UserRole.MANAGER, UserStatus.ACTIVE);
		UserAccount inactive = saveUser("user-zulu-" + marker, UserRole.ADMIN, UserStatus.INACTIVE);

		mockMvc.perform(get("/api/users").param("search", marker).param("size", "10")
				.with(authentication(userPrincipal(alpha))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(1))
				.andExpect(jsonPath("$.data.content[0].username").value(alpha.getUsername()))
				.andExpect(jsonPath("$.data.content[0].role").value("MANAGER"))
				.andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.content[1].username").value(beta.getUsername()))
				.andExpect(jsonPath("$.data.content[2].username").value(inactive.getUsername()))
				.andExpect(jsonPath("$.data.content[2].status").value("INACTIVE"))
				.andExpect(jsonPath("$.data.content[0].fullName").doesNotExist());

		mockMvc.perform(get("/api/users").param("search", marker.toUpperCase())
					.param("role", "MEMBER").param("role", "ADMIN")
					.with(authentication(userPrincipal(alpha))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].username").value(beta.getUsername()))
				.andExpect(jsonPath("$.data.content[1].username").value(inactive.getUsername()));

		mockMvc.perform(get("/api/users").param("search", ("USER-BETA-" + marker).toUpperCase())
				.with(authentication(userPrincipal(alpha))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].email").value(beta.getEmail()));

		mockMvc.perform(get("/api/users").param("search", marker).param("page", "1").param("size", "2")
				.with(authentication(userPrincipal(alpha))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].username").value(inactive.getUsername()));
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	void createsManagedAccountForEachSupportedRoleAsActiveWithoutProfile(UserRole role) throws Exception {
		String marker = UUID.randomUUID().toString().replace("-", "");
		UserAccount creator = saveUser("creator-" + marker, UserRole.MANAGER, UserStatus.ACTIVE);
		String email = "Managed-" + marker + "@GMAIL.COM";

		mockMvc.perform(post("/api/users").with(authentication(userPrincipal(creator))).contentType(MediaType.APPLICATION_JSON)
				.content(managedRequest(email, "managed-" + marker, role)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.role").value(role.name()))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.password").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());

		UserAccount created = userAccountRepository.findByEmailIgnoreCase(email.toLowerCase()).orElseThrow();
		assertEquals(email.toLowerCase(), created.getEmail());
		assertEquals("managed-" + marker, created.getUsername());
		assertEquals(role, created.getRole());
		assertEquals(UserStatus.ACTIVE, created.getStatus());
		assertNotEquals("Password1!", created.getPasswordHash());
		assertTrue(passwordEncoder.matches("Password1!", created.getPasswordHash()));
		assertEquals(0, profileRepository.countByUserIdAndDeletedAtIsNull(created.getId()));
	}

	@Test
	void enforcesCaseInsensitiveEmailAndUsernameUniqueness() throws Exception {
		String marker = UUID.randomUUID().toString().replace("-", "");
		UserAccount creator = saveUser("creator-" + marker, UserRole.ADMIN, UserStatus.ACTIVE);
		String email = "managed-" + marker + "@gmail.com";
		String username = "managed-" + marker;

		mockMvc.perform(post("/api/users").with(authentication(userPrincipal(creator))).contentType(MediaType.APPLICATION_JSON)
				.content(managedRequest(email.toUpperCase(), " " + username + " ", UserRole.MEMBER)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/users").with(authentication(userPrincipal(creator))).contentType(MediaType.APPLICATION_JSON)
				.content(managedRequest(email.toUpperCase(), username + "-other", UserRole.MEMBER)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("AUTH_EMAIL_ALREADY_REGISTERED"));

		mockMvc.perform(post("/api/users").with(authentication(userPrincipal(creator))).contentType(MediaType.APPLICATION_JSON)
				.content(managedRequest("other-" + marker + "@gmail.com", username.toUpperCase(), UserRole.MEMBER)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("AUTH_USERNAME_ALREADY_REGISTERED"));
	}

	private UserAccount saveUser(String username, UserRole role, UserStatus status) {
		return userAccountRepository.saveAndFlush(new UserAccount(username + "@example.com", username, "hash", role, status));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}

	private String managedRequest(String email, String username, UserRole role) {
		return "{\"email\":\"" + email + "\",\"username\":\"" + username + "\",\"password\":\"Password1!\",\"role\":\""
				+ role.name() + "\"}";
	}
}
