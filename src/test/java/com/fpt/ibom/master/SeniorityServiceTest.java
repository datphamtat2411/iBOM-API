package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.SeniorityMutationRequest;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.service.SeniorityService;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class SeniorityServiceTest {

	private final SeniorityRepository seniorityRepository = Mockito.mock(SeniorityRepository.class);
	private final ProfileSkillRepository profileSkillRepository = Mockito.mock(ProfileSkillRepository.class);
	private final SeniorityService seniorityService = new SeniorityService(seniorityRepository, profileSkillRepository);

	@BeforeEach
	void defaults() {
		when(seniorityRepository.findAllByOrderByFromExperienceAscIdAsc()).thenReturn(List.of());
		when(seniorityRepository.saveAndFlush(any(Seniority.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void trimsNameAndOrdersListByRangeAndId() {
		Seniority first = seniority(2L, "Senior", "2", "5");
		Seniority second = seniority(1L, "Junior", "0", "2");

		assertEquals("Senior", seniorityService.create(request("  Senior  ", "2", "5")).name());
		when(seniorityRepository.findAllByOrderByFromExperienceAscIdAsc()).thenReturn(List.of(second, first));
		assertEquals(List.of(1L, 2L), seniorityService.list().stream().map(response -> response.id()).toList());
	}

	@Test
	void rejectsMalformedValuesAndDuplicateNames() {
		assertValidation(() -> seniorityService.create(request(" ", "0", "2")));
		assertValidation(() -> seniorityService.create(request("Junior", "-1", "2")));
		assertValidation(() -> seniorityService.create(request("Junior", "2", "2")));
		assertValidation(() -> seniorityService.create(request("Junior", "2", "1")));
		when(seniorityRepository.existsByNameIgnoreCase("junior")).thenReturn(true);
		ApiException duplicate = assertThrows(ApiException.class,
				() -> seniorityService.create(request(" junior ", "0", "2")));
		assertEquals(ErrorCode.SENIORITY_NAME_ALREADY_EXISTS, duplicate.getErrorCode());
		assertEquals(HttpStatus.CONFLICT, duplicate.getStatus());
	}

	@Test
	void rejectsOverlapsButAllowsTouchingBoundariesAndGaps() {
		when(seniorityRepository.findAllByOrderByFromExperienceAscIdAsc())
				.thenReturn(List.of(seniority(1L, "Junior", "0", "2"), seniority(2L, "Senior", "4", "6")));

		assertThrowsWithCode(() -> seniorityService.create(request("Overlap", "1", "4")),
				ErrorCode.SENIORITY_RANGE_CONFLICT);
		seniorityService.create(request("Touching", "2", "4"));
		seniorityService.create(request("Gap", "7", "8"));
	}

	@Test
	void enforcesUnlimitedRules() {
		when(seniorityRepository.findAllByOrderByFromExperienceAscIdAsc())
				.thenReturn(List.of(seniority(1L, "Senior", "2", "5"), seniority(2L, "Lead", "5", null)));

		assertThrowsWithCode(() -> seniorityService.create(request("Other Unlimited", "6", null)),
				ErrorCode.SENIORITY_RANGE_CONFLICT);
		assertThrowsWithCode(() -> seniorityService.create(request("Before Upper", "4", null)),
				ErrorCode.SENIORITY_RANGE_CONFLICT);
	}

	@Test
	void updatesExcludingCurrentRangeAndCanReclassifyWithoutProfileSkillWrites() {
		Seniority current = seniority(1L, "Junior", "0", "2");
		when(seniorityRepository.findById(1L)).thenReturn(Optional.of(current));
		when(seniorityRepository.existsByNameIgnoreCaseAndIdNot("Junior Updated", 1L)).thenReturn(false);
		when(seniorityRepository.findAllByOrderByFromExperienceAscIdAsc()).thenReturn(List.of(current));

		assertEquals("Junior Updated", seniorityService.update(1L, request(" Junior Updated ", "2", "5")).name());
		verify(profileSkillRepository, never()).save(any());
	}

	@Test
	void protectsFiniteAndUnlimitedRangesFromDeletionWhenInUse() {
		Seniority finite = seniority(1L, "Junior", "0", "2");
		when(seniorityRepository.findById(1L)).thenReturn(Optional.of(finite));
		when(profileSkillRepository.existsByExperienceYearsGreaterThanEqualAndExperienceYearsLessThan(
				eq(new BigDecimal("0")), eq(new BigDecimal("2")))).thenReturn(true);
		assertThrowsWithCode(() -> seniorityService.delete(1L), ErrorCode.SENIORITY_IN_USE);

		Seniority unlimited = seniority(2L, "Lead", "5", null);
		when(seniorityRepository.findById(2L)).thenReturn(Optional.of(unlimited));
		when(profileSkillRepository.existsByExperienceYearsGreaterThanEqual(new BigDecimal("5"))).thenReturn(true);
		assertThrowsWithCode(() -> seniorityService.delete(2L), ErrorCode.SENIORITY_IN_USE);
	}

	@Test
	void reportsMissingSeniority() {
		when(seniorityRepository.findById(42L)).thenReturn(Optional.empty());
		ApiException exception = assertThrows(ApiException.class, () -> seniorityService.update(42L, request("x", "0", "1")));
		assertEquals(ErrorCode.SENIORITY_NOT_FOUND, exception.getErrorCode());
	}

	private void assertValidation(Runnable action) {
		assertThrowsWithCode(action, ErrorCode.VALIDATION_ERROR);
	}

	private void assertThrowsWithCode(Runnable action, ErrorCode code) {
		ApiException exception = assertThrows(ApiException.class, action::run);
		assertEquals(code, exception.getErrorCode());
	}

	private SeniorityMutationRequest request(String name, String from, String to) {
		return new SeniorityMutationRequest(name, new BigDecimal(from), to == null ? null : new BigDecimal(to));
	}

	private Seniority seniority(Long id, String name, String from, String to) {
		Seniority seniority = new Seniority(name, new BigDecimal(from), to == null ? null : new BigDecimal(to));
		ReflectionTestUtils.setField(seniority, "id", id);
		return seniority;
	}
}
