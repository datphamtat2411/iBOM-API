package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.EducationMutationResponse;
import com.fpt.ibom.profile.dto.EducationRequest;
import com.fpt.ibom.profile.dto.EducationResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.EducationService;
import com.fpt.ibom.profile.service.ProfileVersionService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class EducationServiceTest {

	private final EducationRepository educations = org.mockito.Mockito.mock(EducationRepository.class);
	private final ProfileAccessService profileAccess = org.mockito.Mockito.mock(ProfileAccessService.class);
	private final ProfileVersionService profileVersions = org.mockito.Mockito.mock(ProfileVersionService.class);
	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);
	private final EducationService service = new EducationService(educations, profileAccess, profileVersions);

	@Test
	void listsEducationForActiveOwnedProfileInIdOrder() {
		Profile profile = profile();
		Education first = education(profile, "First School", EducationStatus.ONGOING, LocalDate.of(2020, 1, 1), null);
		Education second = education(profile, "Second School", EducationStatus.COMPLETED, LocalDate.of(2018, 1, 1),
				LocalDate.of(2019, 1, 1));
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(educations.findByProfileIdOrderByIdAsc(8L)).thenReturn(List.of(first, second));

		List<EducationResponse> result = service.list(principal, 8L);

		assertEquals(List.of("First School", "Second School"), result.stream().map(EducationResponse::schoolName).toList());
		verify(educations).findByProfileIdOrderByIdAsc(8L);
	}

	@Test
	void createsOngoingEducationWithCanonicalTextAndNullEndDate() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(educations.saveAndFlush(any(Education.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnAdvance(profile);

		EducationMutationResponse result = service.create(principal, 8L,
				new EducationRequest(" School ", " Degree ", "   ", LocalDate.of(2020, 1, 1),
						LocalDate.of(2025, 1, 1), " ongoing ", 0L));

		ArgumentCaptor<Education> captor = ArgumentCaptor.forClass(Education.class);
		verify(educations).saveAndFlush(captor.capture());
		Education saved = captor.getValue();
		assertEquals("School", saved.getSchoolName());
		assertEquals("Degree", saved.getDegree());
		assertNull(saved.getFieldOfStudy());
		assertNull(saved.getEndDate());
		assertEquals(EducationStatus.ONGOING, saved.getStatus());
		assertEquals(1L, result.profileVersion());
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsCompletedEducationWithoutEndDate() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("COMPLETED", LocalDate.of(2020, 1, 1), null, 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.EDUCATION_END_DATE_REQUIRED, exception.getErrorCode());
		verify(educations, never()).saveAndFlush(any());
	}

	@Test
	void rejectsEducationWithInvalidDateRange() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("COMPLETED", LocalDate.of(2021, 1, 1), LocalDate.of(2020, 1, 1), 0L)));

		assertEquals(ErrorCode.EDUCATION_DATE_RANGE_INVALID, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void rejectsUnsupportedEducationStatus() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("WITHDRAWN", LocalDate.of(2020, 1, 1), null, 0L)));

		assertEquals(ErrorCode.EDUCATION_INVALID_STATUS, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void updatesEducationOnlyWhenOwnedBySuppliedProfile() {
		Profile profile = profile();
		Education education = education(profile, "Original", EducationStatus.ONGOING, LocalDate.of(2020, 1, 1), null);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(educations.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(education));
		when(educations.saveAndFlush(education)).thenReturn(education);
		simulateVersionIncrementOnAdvance(profile);

		EducationMutationResponse result = service.update(principal, 8L, 12L,
				request("COMPLETED", LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1), 0L));

		assertEquals(EducationStatus.COMPLETED, result.education().status());
		assertEquals(1L, result.profileVersion());
		assertEquals(EducationStatus.COMPLETED, education.getStatus());
		assertEquals(LocalDate.of(2022, 1, 1), education.getEndDate());
		verify(educations).findByIdAndProfileId(12L, 8L);
		verify(profileVersions).advance(profile);
	}

	@Test
	void physicallyDeletesOwnedEducationAndReturnsProfileVersion() {
		Profile profile = profile();
		Education education = education(profile, "School", EducationStatus.ONGOING, LocalDate.of(2020, 1, 1), null);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(educations.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(education));
		simulateVersionIncrementOnAdvance(profile);

		ProfileVersionResponse result = service.delete(principal, 8L, 12L, 0L);

		assertEquals(1L, result.profileVersion());
		verify(educations).delete(education);
		verify(educations, never()).saveAndFlush(any());
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsStaleVersionBeforeChildMutation() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("ONGOING", LocalDate.of(2020, 1, 1), null, 1L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(educations, never()).saveAndFlush(any());
	}

	@Test
	void translatesOptimisticLockFailure() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(educations.saveAndFlush(any(Education.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doThrow(new OptimisticLockException()).when(profileVersions).advance(any());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("ONGOING", LocalDate.of(2020, 1, 1), null, 0L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
	}

	@Test
	void returnsNotFoundForForeignEducationId() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());
		when(educations.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(principal, 8L, 12L, 0L));

		assertEquals(ErrorCode.EDUCATION_NOT_FOUND, exception.getErrorCode());
		verify(educations, never()).delete(any());
	}

	private Profile profile() {
		return new Profile(user(), "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private Education education(Profile profile, String schoolName, EducationStatus status, LocalDate startDate,
			LocalDate endDate) {
		return new Education(profile, schoolName, "Degree", null, startDate, endDate, status);
	}

	private EducationRequest request(String status, LocalDate startDate, LocalDate endDate, long version) {
		return new EducationRequest("School", "Degree", "Field", startDate, endDate, status, version);
	}

	private void simulateVersionIncrementOnAdvance(Profile profile) {
		org.mockito.Mockito.doAnswer(invocation -> {
			long nextVersion = profile.getVersion() + 1;
			ReflectionTestUtils.setField(profile, "version", nextVersion);
			ReflectionTestUtils.setField(profile, "hasPreviewed", false);
			return nextVersion;
		}).when(profileVersions).advance(profile);
	}
}
