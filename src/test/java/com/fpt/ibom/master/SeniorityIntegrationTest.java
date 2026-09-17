package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.repository.SeniorityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SeniorityIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SeniorityRepository seniorityRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesSenioritySchemaAndConstraints() {
		assertTrue(jdbcTemplate.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seniorities'", String.class).contains("seniorities"));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seniorities' AND COLUMN_NAME = 'from_experience'", String.class));
		assertEquals(5, jdbcTemplate.queryForObject("SELECT NUMERIC_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seniorities' AND COLUMN_NAME = 'from_experience'", Integer.class));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seniorities' AND INDEX_NAME = 'uk_seniorities_name_ci'", Integer.class));
	}

	@Test
	void seniorityCrudPersistsOrderingAndCaseInsensitiveNames() throws Exception {
		String marker = UUID.randomUUID().toString().substring(0, 8);
		UserAccount user = userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"seniority-" + marker, "hash", UserRole.MANAGER, UserStatus.ACTIVE));
		String prefix = "Seniority-" + marker;
		mockMvc.perform(post("/api/master/seniority").with(authentication(userPrincipal(user)))
				.contentType("application/json").content(requestJson(prefix + " Senior", "2", "5")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value(prefix + " Senior"));
		mockMvc.perform(post("/api/master/seniority").with(authentication(userPrincipal(user)))
				.contentType("application/json").content(requestJson(prefix + " Junior", "0", "2")))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/master/seniority").with(authentication(userPrincipal(user)))
				.contentType("application/json").content(requestJson(prefix + " senior", "5", "8")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SENIORITY_NAME_ALREADY_EXISTS"));

		mockMvc.perform(get("/api/master/seniority").with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value(prefix + " Junior"))
				.andExpect(jsonPath("$.data[1].name").value(prefix + " Senior"));
		assertTrue(seniorityRepository.findAll().stream().allMatch(value -> value.getFromExperience() != null));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}

	private String requestJson(String name, String from, String to) {
		return "{\"name\":\"" + name + "\",\"fromExperience\":" + from + ",\"toExperience\":" + to + "}";
	}
}
