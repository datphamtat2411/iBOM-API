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
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.EducationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class EducationServiceTest {

	private final EducationRepository educations = org.mockito.Mockito.mock(EducationRepository.class);
	private final ProfileRepository profiles = org.mockito.Mockito.mock(ProfileRepository.class);
	private final EntityManager entityManager = org.mockito.Mockito.mock(EntityManager.class);
	private final EducationService service = new EducationService(educations, profiles, entityManager);

	@Test
	void listsEducationForActiveOwnedProfileInIdOrder() {
		Profile profile = profile();
		Education first = education(profile, "First School", EducationStatus.ONGOING, LocalDate.of(2020, 1, 1), null);
		Education second = education(profile, "Second School", EducationStatus.COMPLETED, LocalDate.of(2018, 1, 1),
				LocalDate.of(2019, 1, 1));
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(educations.findByProfileIdOrderByIdAsc(8L)).thenReturn(List.of(first, second));

		List<EducationResponse> result = service.list(7L, 8L);

		assertEquals(List.of("First School", "Second School"), result.stream().map(EducationResponse::schoolName).toList());
		verify(educations).findByProfileIdOrderByIdAsc(8L);
	}

	@Test
	void createsOngoingEducationWithCanonicalTextAndNullEndDate() {
		Profile profile = profile();
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(educations.save(any(Education.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnRefresh(profile);

		EducationMutationResponse result = service.create(7L, 8L,
				new EducationRequest(" School ", " Degree ", "   ", LocalDate.of(2020, 1, 1),
						LocalDate.of(2025, 1, 1), " ongoing ", 0L));

		ArgumentCaptor<Education> captor = ArgumentCaptor.forClass(Education.class);
		verify(educations).save(captor.capture());
		Education saved = captor.getValue();
		assertEquals("School", saved.getSchoolName());
		assertEquals("Degree", saved.getDegree());
		assertNull(saved.getFieldOfStudy());
		assertNull(saved.getEndDate());
		assertEquals(EducationStatus.ONGOING, saved.getStatus());
		assertEquals(1L, result.profileVersion());
		verify(entityManager).lock(profile, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		verify(entityManager).refresh(profile);
	}

	@Test
	void rejectsCompletedEducationWithoutEndDate() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile()));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				request("COMPLETED", LocalDate.of(2020, 1, 1), null, 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.EDUCATION_END_DATE_REQUIRED, exception.getErrorCode());
		verify(educations, never()).save(any());
	}

	@Test
	void rejectsEducationWithInvalidDateRange() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile()));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				request("COMPLETED", LocalDate.of(2021, 1, 1), LocalDate.of(2020, 1, 1), 0L)));

		assertEquals(ErrorCode.EDUCATION_DATE_RANGE_INVALID, exception.getErrorCode());
		verify(entityManager, never()).lock(any(), any());
	}

	@Test
	void rejectsUnsupportedEducationStatus() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile()));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				request("WITHDRAWN", LocalDate.of(2020, 1, 1), null, 0L)));

		assertEquals(ErrorCode.EDUCATION_INVALID_STATUS, exception.getErrorCode());
		verify(entityManager, never()).lock(any(), any());
	}

	@Test
	void updatesEducationOnlyWhenOwnedBySuppliedProfile() {
		Profile profile = profile();
		Education education = education(profile, "Original", EducationStatus.ONGOING, LocalDate.of(2020, 1, 1), null);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(educations.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(education));
		when(educations.save(education)).thenReturn(education);
		simulateVersionIncrementOnRefresh(profile);

		EducationMutationResponse result = service.update(7L, 8L, 12L,
				request("COMPLETED", LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1), 0L));

		assertEquals(EducationStatus.COMPLETED, result.education().status());
		assertEquals(1L, result.profileVersion());
		assertEquals(EducationStatus.COMPLETED, education.getStatus());
		assertEquals(LocalDate.of(2022, 1, 1), education.getEndDate());
		verify(educations).findByIdAndProfileId(12L, 8L);
		verify(entityManager).refresh(profile);
	}

	@Test
	void physicallyDeletesOwnedEducationAndReturnsProfileVersion() {
		Profile profile = profile();
		Education education = education(profile, "School", EducationStatus.ONGOING, LocalDate.of(2020, 1, 1), null);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(educations.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(education));
		simulateVersionIncrementOnRefresh(profile);

		ProfileVersionResponse result = service.delete(7L, 8L, 12L, 0L);

		assertEquals(1L, result.profileVersion());
		verify(educations).delete(education);
		verify(educations, never()).save(any());
		verify(entityManager).refresh(profile);
	}

	@Test
	void rejectsStaleVersionBeforeChildMutation() {
		Profile profile = profile();
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				request("ONGOING", LocalDate.of(2020, 1, 1), null, 1L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		verify(entityManager, never()).lock(any(), any());
		verify(educations, never()).save(any());
	}

	@Test
	void translatesOptimisticLockFailure() {
		Profile profile = profile();
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(educations.save(any(Education.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doThrow(new OptimisticLockException()).when(entityManager).flush();

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				request("ONGOING", LocalDate.of(2020, 1, 1), null, 0L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
	}

	@Test
	void returnsNotFoundForForeignEducationId() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile()));
		when(educations.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(7L, 8L, 12L, 0L));

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

	private void simulateVersionIncrementOnRefresh(Profile profile) {
		org.mockito.Mockito.doAnswer(invocation -> {
			ReflectionTestUtils.setField(profile, "version", profile.getVersion() + 1);
			return null;
		}).when(entityManager).refresh(profile);
	}
}
