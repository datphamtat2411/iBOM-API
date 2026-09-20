package com.fpt.ibom.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class UserIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

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

	private UserAccount saveUser(String username, UserRole role, UserStatus status) {
		return userAccountRepository.saveAndFlush(new UserAccount(username + "@example.com", username, "hash", role, status));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}
}
