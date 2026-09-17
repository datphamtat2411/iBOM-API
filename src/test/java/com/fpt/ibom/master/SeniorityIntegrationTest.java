package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.dto.SeniorityMutationRequest;
import com.fpt.ibom.master.dto.SeniorityResponse;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.service.SeniorityService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SeniorityIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

 @Autowired
	private SeniorityRepository seniorityRepository;

	@Autowired
	private SeniorityService seniorityService;

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

	@Test
	void crudResponsesExposePersistedAuditTimestampsAndExistingFields() throws Exception {
		String marker = UUID.randomUUID().toString().substring(0, 8);
		UserAccount user = userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"seniority-timestamps-" + marker, "hash", UserRole.MANAGER, UserStatus.ACTIVE));
		String name = "Timestamp Seniority-" + marker;

		MvcResult createResult = mockMvc.perform(post("/api/master/seniority").with(authentication(userPrincipal(user)))
				.contentType("application/json").content(requestJson(name, "100", "110")))
				.andExpect(status().isCreated()).andReturn();
		Seniority created = seniorityRepository.findAll().stream().filter(value -> value.getName().equals(name)).findFirst()
				.orElseThrow();
		assertNotNull(created.getCreatedAt());
		assertNotNull(created.getUpdatedAt());
		JsonNode createData = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
		assertEquals(name, createData.path("name").asText());
		assertEquals(new BigDecimal("100"), createData.path("fromExperience").decimalValue());
		assertEquals(new BigDecimal("110"), createData.path("toExperience").decimalValue());
		assertEquals(created.getCreatedAt().toString(), createData.path("createdAt").asText());
		assertEquals(created.getUpdatedAt().toString(), createData.path("updatedAt").asText());

		mockMvc.perform(get("/api/master/seniority").with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[*].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.data[*].updatedAt").isNotEmpty());

		MvcResult updateResult = mockMvc.perform(put("/api/master/seniority/" + created.getId())
				.with(authentication(userPrincipal(user))).contentType("application/json")
				.content(requestJson(name + " Updated", "110", "120"))).andExpect(status().isOk()).andReturn();
		Seniority updated = seniorityRepository.findById(created.getId()).orElseThrow();
		JsonNode updateData = objectMapper.readTree(updateResult.getResponse().getContentAsString()).path("data");
		assertEquals(created.getCreatedAt().toString(), updateData.path("createdAt").asText());
		assertEquals(updated.getUpdatedAt().toString(), updateData.path("updatedAt").asText());
		assertEquals("Timestamp Seniority-" + marker + " Updated", updateData.path("name").asText());
		assertEquals(new BigDecimal("110"), updateData.path("fromExperience").decimalValue());
		assertEquals(new BigDecimal("120"), updateData.path("toExperience").decimalValue());
	}

	@Test
	void concurrentOverlappingFiniteCreatesAllowOnlyOneMutation() throws Exception {
		String marker = "concurrent-finite-" + UUID.randomUUID();

		List<MutationAttempt> attempts = concurrently(
				() -> seniorityService.create(request(marker + "-first", "100", "110")),
				() -> seniorityService.create(request(marker + "-second", "105", "115")));

		assertOneRangeConflict(attempts);
		assertRangeInvariants();
	}

	@Test
	void concurrentUnlimitedCreatesAllowOnlyOneMutation() throws Exception {
		String marker = "concurrent-unlimited-" + UUID.randomUUID();

		List<MutationAttempt> attempts = concurrently(
				() -> seniorityService.create(request(marker + "-first", "900", null)),
				() -> seniorityService.create(request(marker + "-second", "910", null)));

		assertOneRangeConflict(attempts);
		assertRangeInvariants();
	}

	@Test
	void concurrentUpdateAndCreateAllowOnlyOneConflictingMutation() throws Exception {
		String marker = "concurrent-update-" + UUID.randomUUID();
		Seniority existing = seniorityRepository.saveAndFlush(new Seniority(marker + "-existing",
				new BigDecimal("300"), new BigDecimal("310")));

		List<MutationAttempt> attempts = concurrently(
				() -> seniorityService.update(existing.getId(), request(marker + "-updated", "320", "330")),
				() -> seniorityService.create(request(marker + "-created", "325", "335")));

		assertOneRangeConflict(attempts);
		assertRangeInvariants();
	}

	@Test
	void concurrentNonConflictingMutationsAndTouchingUpdateRemainSuccessful() throws Exception {
		String marker = "concurrent-valid-" + UUID.randomUUID();
		List<MutationAttempt> creates = concurrently(
				() -> seniorityService.create(request(marker + "-first", "400", "410")),
				() -> seniorityService.create(request(marker + "-second", "420", "430")));

		assertTrue(creates.stream().allMatch(attempt -> attempt.exception() == null));
		Long firstId = creates.get(0).response().id();
		MutationAttempt update = attempt(() -> seniorityService.update(firstId,
				request(marker + "-first-updated", "410", "420")));

		assertTrue(update.exception() == null);
		assertRangeInvariants();
	}

	private List<MutationAttempt> concurrently(Callable<SeniorityResponse> first,
			Callable<SeniorityResponse> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Future<MutationAttempt> firstFuture = executor.submit(() -> awaitAndAttempt(first, ready, start));
		Future<MutationAttempt> secondFuture = executor.submit(() -> awaitAndAttempt(second, ready, start));
		try {
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			return List.of(firstFuture.get(20, TimeUnit.SECONDS), secondFuture.get(20, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	private MutationAttempt awaitAndAttempt(Callable<SeniorityResponse> mutation, CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await(10, TimeUnit.SECONDS);
		return attempt(mutation);
	}

	private MutationAttempt attempt(Callable<SeniorityResponse> mutation) {
		try {
			return new MutationAttempt(mutation.call(), null);
		} catch (Exception exception) {
			return new MutationAttempt(null, exception);
		}
	}

	private void assertOneRangeConflict(List<MutationAttempt> attempts) {
		assertEquals(1, attempts.stream().filter(attempt -> attempt.exception() == null).count(),
				attempts.stream().map(attempt -> attempt.exception() == null ? "success" : attempt.exception().toString()).toList()
						.toString());
		List<MutationAttempt> failures = attempts.stream().filter(attempt -> attempt.exception() != null).toList();
		assertEquals(1, failures.size());
		assertTrue(failures.get(0).exception() instanceof ApiException);
		assertEquals(ErrorCode.SENIORITY_RANGE_CONFLICT,
				((ApiException) failures.get(0).exception()).getErrorCode());
	}

	private void assertRangeInvariants() {
		List<Seniority> ranges = new ArrayList<>(seniorityRepository.findAll());
		assertTrue(ranges.stream().allMatch(range -> range.getFromExperience() != null
				&& range.getFromExperience().compareTo(BigDecimal.ZERO) >= 0
				&& (range.getToExperience() == null || range.getToExperience().compareTo(range.getFromExperience()) > 0)));
		assertTrue(ranges.stream().filter(range -> range.getToExperience() == null).count() <= 1);
		for (int i = 0; i < ranges.size(); i++) {
			for (int j = i + 1; j < ranges.size(); j++) {
				assertTrue(!overlaps(ranges.get(i), ranges.get(j)));
			}
		}
		BigDecimal highestFiniteUpper = ranges.stream().map(Seniority::getToExperience).filter(value -> value != null)
				.max(BigDecimal::compareTo).orElse(null);
		if (highestFiniteUpper != null) {
			assertTrue(ranges.stream().filter(range -> range.getToExperience() == null)
					.allMatch(range -> range.getFromExperience().compareTo(highestFiniteUpper) >= 0));
		}
	}

	private boolean overlaps(Seniority left, Seniority right) {
		return lessThan(left.getFromExperience(), right.getToExperience())
				&& lessThan(right.getFromExperience(), left.getToExperience());
	}

	private boolean lessThan(BigDecimal value, BigDecimal upperBound) {
		return upperBound == null || value.compareTo(upperBound) < 0;
	}

	private SeniorityMutationRequest request(String name, String from, String to) {
		return new SeniorityMutationRequest(name, new BigDecimal(from), to == null ? null : new BigDecimal(to));
	}

	private record MutationAttempt(SeniorityResponse response, Exception exception) {
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
