package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileRequest;
import com.fpt.ibom.profile.dto.ProfileUpdateRequest;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.hibernate.exception.ConstraintViolationException;

class ProfileServiceTest {
	private final ProfileRepository profiles = mock(ProfileRepository.class);
	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final ProfileService service = new ProfileService(profiles, users);

	@Test
	void createsProfileForPrincipalUserAndInitializesPreviewState() {
		UserAccount user = new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Default")).thenReturn(false);
		when(users.findById(7L)).thenReturn(java.util.Optional.of(user));
		when(profiles.saveAndFlush(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.create(7L, request());

		ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
		verify(profiles).saveAndFlush(captor.capture());
		assertEquals(user, captor.getValue().getUser());
		assertEquals("Default", captor.getValue().getProfileName());
		assertEquals("First", captor.getValue().getFirstName());
		assertEquals("Last", captor.getValue().getLastName());
		assertEquals("Engineer", captor.getValue().getJobTitle());
		assertEquals(new BigDecimal("3.5"), captor.getValue().getYearsOfExperience());
		assertEquals("Personality", captor.getValue().getPersonality());
		assertEquals("Technical summary", captor.getValue().getTechnicalSummary());
		assertEquals(false, captor.getValue().isHasPreviewed());
		assertEquals(0L, captor.getValue().getVersion());
	}

	@Test
	void rejectsDuplicateActiveNameWithProfileConflict() {
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Default")).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, request()));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, exception.getErrorCode());
		verify(users, never()).findById(any());
	}

	@Test
	void listsOwnedActiveProfilesInRepositoryOrder() {
		Profile first = new Profile(user(), "First", "First", "Name", "Engineer", new BigDecimal("1.0"),
				"Personality", "Summary");
		Profile second = new Profile(user(), "Second", "Second", "Name", "Manager", new BigDecimal("2.0"),
				"Personality", "Summary");
		when(profiles.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(7L)).thenReturn(List.of(first, second));

		List<ProfileSummaryResponse> result = service.list(7L);

		assertEquals(List.of("First", "Second"), result.stream().map(ProfileSummaryResponse::profileName).toList());
	}

	@Test
	void returnsEmptyListWhenUserHasNoActiveProfiles() {
		when(profiles.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(7L)).thenReturn(List.of());

		assertEquals(List.of(), service.list(7L));
	}

	@Test
	void mapsOwnedProfileDetailsIncludingVersion() {
		Profile profile = new Profile(user(), "Default", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Summary");
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));

		ProfileDetailResponse result = service.get(7L, 8L);

		assertEquals("Default", result.profileName());
		assertEquals("First", result.firstName());
		assertEquals("Last", result.lastName());
		assertEquals(new BigDecimal("3.5"), result.yearsOfExperience());
		assertEquals(0L, result.version());
	}

	@Test
	void raisesProfileNotFoundWhenOwnedLookupIsAbsent() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.get(7L, 8L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	void updatesOwnedProfileAndResetsPreviewState() {
		Profile profile = new Profile(user(), "Default", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Summary");
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCaseAndIdNot(7L, "Updated", 8L))
				.thenReturn(false);
		when(profiles.saveAndFlush(profile)).thenReturn(profile);

		ProfileDetailResponse result = service.update(7L, 8L, updateRequest(0L));

		assertEquals("Updated", result.profileName());
		assertEquals("First", result.firstName());
		assertEquals("Last", result.lastName());
		assertEquals("Developer", result.jobTitle());
		assertEquals(new BigDecimal("0.5"), result.yearsOfExperience());
		assertEquals("Friendly", result.personality());
		assertEquals("Technical summary", result.technicalSummary());
		assertEquals(false, result.hasPreviewed());
	}

	@Test
	void rejectsStaleProfileVersionBeforeMutation() {
		Profile profile = new Profile(user(), "Default", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Summary");
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));

		ApiException exception = assertThrows(ApiException.class, () -> service.update(7L, 8L, updateRequest(1L)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		verify(profiles, never()).saveAndFlush(any());
	}

	@Test
	void translatesConcurrentOptimisticLockFailure() {
		Profile profile = new Profile(user(), "Default", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Summary");
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCaseAndIdNot(7L, "Updated", 8L))
				.thenReturn(false);
		doThrow(new ObjectOptimisticLockingFailureException(Profile.class, 8L)).when(profiles).saveAndFlush(profile);

		ApiException exception = assertThrows(ApiException.class, () -> service.update(7L, 8L, updateRequest(0L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
	}

	@Test
	void translatesProfileNameUniqueConstraintFailure() {
		UserAccount user = user();
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Default")).thenReturn(false);
		when(users.findById(7L)).thenReturn(Optional.of(user));
		ConstraintViolationException violation = new ConstraintViolationException("duplicate", null,
				"uk_profiles_user_active_name");
		when(profiles.saveAndFlush(any(Profile.class))).thenThrow(new DataIntegrityViolationException("duplicate", violation));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, request()));

		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, exception.getErrorCode());
	}

	@Test
	void doesNotTranslateUnrelatedIntegrityFailureAsDuplicateName() {
		UserAccount user = user();
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Default")).thenReturn(false);
		when(users.findById(7L)).thenReturn(Optional.of(user));
		ConstraintViolationException violation = new ConstraintViolationException("constraint", null,
				"chk_profiles_years_of_experience");
		DataIntegrityViolationException integrityFailure = new DataIntegrityViolationException("constraint", violation);
		when(profiles.saveAndFlush(any(Profile.class))).thenThrow(integrityFailure);

		DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
				() -> service.create(7L, request()));

		assertSame(integrityFailure, exception);
	}

	@Test
	void rejectsDuplicateNameExcludingCurrentProfile() {
		Profile profile = new Profile(user(), "Default", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Summary");
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCaseAndIdNot(7L, "Updated", 8L))
				.thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.update(7L, 8L, updateRequest(0L)));

		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, exception.getErrorCode());
	}

	@Test
	void softDeletesOwnedActiveProfileWithoutPhysicalDeletion() {
		UserAccount user = user();
		Profile profile = new Profile(user, "Default", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Summary");
		when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(profiles.countByUserIdAndDeletedAtIsNull(7L)).thenReturn(2L);

		service.delete(7L, 8L);

		assertEquals(false, profile.getDeletedAt() == null);
		verify(profiles).saveAndFlush(profile);
		verify(profiles, never()).delete(any());
	}

	@Test
	void rejectsDeletingLastActiveProfile() {
		UserAccount user = user();
		Profile profile = new Profile(user, "Default", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Summary");
		when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(profiles.countByUserIdAndDeletedAtIsNull(7L)).thenReturn(1L);

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(7L, 8L));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_LAST_ACTIVE_CANNOT_DELETE, exception.getErrorCode());
		verify(profiles, never()).saveAndFlush(any());
	}

	@Test
	void rejectsMissingDeletedOrForeignProfileThroughActiveOwnedLookup() {
		when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user()));
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(7L, 8L));

		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
		verify(profiles, never()).countByUserIdAndDeletedAtIsNull(any());
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private ProfileRequest request() {
		return new ProfileRequest(" Default ", " First ", " Last ", " Engineer ", new BigDecimal("3.5"),
				" Personality ", " Technical summary ");
	}

	private ProfileUpdateRequest updateRequest(Long version) {
		return new ProfileUpdateRequest(" Updated ", " First ", " Last ", " Developer ", new BigDecimal("0.5"),
				" Friendly ", " Technical summary ", version);
	}
}
