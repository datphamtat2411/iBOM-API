package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.profile.dto.ProfileLanguageMutationResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageRequest;
import com.fpt.ibom.profile.dto.ProfileLanguageResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import com.fpt.ibom.profile.service.ProfileVersionService;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileLanguageServiceTest {

	private final ProfileLanguageRepository profileLanguages = org.mockito.Mockito.mock(ProfileLanguageRepository.class);
	private final ProfileAccessService profileAccess = org.mockito.Mockito.mock(ProfileAccessService.class);
	private final LanguageRepository languages = org.mockito.Mockito.mock(LanguageRepository.class);
	private final ProfileVersionService profileVersions = org.mockito.Mockito.mock(ProfileVersionService.class);
	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);
	private final ProfileLanguageService service = new ProfileLanguageService(profileLanguages, profileAccess, languages,
			profileVersions);

	@Test
	void listsOwnedLanguagesByProficiencyThenCaseInsensitiveNameAndId() {
		Profile profile = profile();
		ProfileLanguage beginnerZulu = profileLanguage(profile, language(12L, "zulu"), LanguageLevel.BEGINNER);
		ProfileLanguage nativeEnglish = profileLanguage(profile, language(13L, "English"), LanguageLevel.NATIVE);
		ProfileLanguage advancedAlpha = profileLanguage(profile, language(14L, "alpha"), LanguageLevel.ADVANCED);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileLanguages.findByProfileId(8L)).thenReturn(List.of(beginnerZulu, nativeEnglish, advancedAlpha));

		List<ProfileLanguageResponse> result = service.list(principal, 8L);

		assertEquals(List.of("English", "alpha", "zulu"), result.stream().map(ProfileLanguageResponse::languageName).toList());
		assertEquals(List.of(LanguageLevel.NATIVE, LanguageLevel.ADVANCED, LanguageLevel.BEGINNER),
				result.stream().map(ProfileLanguageResponse::level).toList());
	}

	@Test
	void createsExistingLanguageWithCanonicalLevelAndInvalidatesPreview() {
		Profile profile = profile();
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		Language language = language(22L, "English");
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(languages.findById(22L)).thenReturn(Optional.of(language));
		when(profileLanguages.saveAndFlush(any(ProfileLanguage.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnAdvance(profile);

		ProfileLanguageMutationResponse result = service.create(principal, 8L,
				new ProfileLanguageRequest(22L, " upper_intermediate ", 0L));

		ArgumentCaptor<ProfileLanguage> captor = ArgumentCaptor.forClass(ProfileLanguage.class);
		verify(profileLanguages).saveAndFlush(captor.capture());
		ProfileLanguage saved = captor.getValue();
		assertEquals(language, saved.getLanguage());
		assertEquals(LanguageLevel.UPPER_INTERMEDIATE, saved.getLevel());
		assertFalse(profile.isHasPreviewed());
		assertEquals(1L, result.profileVersion());
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsUnsupportedLevelBeforeMutation() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileLanguageRequest(22L, "fluent", 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_LANGUAGE_INVALID_LEVEL, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(languages, never()).findById(any());
	}

	@Test
	void rejectsMissingLanguageAndDuplicateAssignment() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(languages.findById(22L)).thenReturn(Optional.empty());

		ApiException missing = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileLanguageRequest(22L, "NATIVE", 0L)));
		assertEquals(ErrorCode.LANGUAGE_NOT_FOUND, missing.getErrorCode());

		when(languages.findById(22L)).thenReturn(Optional.of(language(22L, "English")));
		when(profileLanguages.existsByProfileIdAndLanguageId(8L, 22L)).thenReturn(true);
		ApiException duplicate = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileLanguageRequest(22L, "NATIVE", 0L)));
		assertEquals(ErrorCode.PROFILE_LANGUAGE_ALREADY_EXISTS, duplicate.getErrorCode());
		verify(profileLanguages, never()).saveAndFlush(any());
	}

	@Test
	void updatesLanguageAndLevelUsingProfileScopedAssociationLookup() {
		Profile profile = profile();
		ProfileLanguage association = profileLanguage(profile, language(22L, "English"), LanguageLevel.BEGINNER);
		Language replacement = language(23L, "Vietnamese");
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileLanguages.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(association));
		when(languages.findById(23L)).thenReturn(Optional.of(replacement));
		when(profileLanguages.saveAndFlush(association)).thenReturn(association);
		simulateVersionIncrementOnAdvance(profile);

		ProfileLanguageMutationResponse result = service.update(principal, 8L, 12L,
				new ProfileLanguageRequest(23L, " advanced ", 0L));

		assertEquals(replacement, association.getLanguage());
		assertEquals(LanguageLevel.ADVANCED, association.getLevel());
		assertEquals(1L, result.profileVersion());
		verify(profileLanguages).existsByProfileIdAndLanguageIdAndIdNot(8L, 23L, 12L);
		verify(profileLanguages).findByIdAndProfileId(12L, 8L);
	}

	@Test
	void rejectsDuplicateReplacementLanguageDuringUpdate() {
		Profile profile = profile();
		ProfileLanguage association = profileLanguage(profile, language(22L, "English"), LanguageLevel.BEGINNER);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileLanguages.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(association));
		when(languages.findById(23L)).thenReturn(Optional.of(language(23L, "Vietnamese")));
		when(profileLanguages.existsByProfileIdAndLanguageIdAndIdNot(8L, 23L, 12L)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.update(principal, 8L, 12L,
				new ProfileLanguageRequest(23L, "ADVANCED", 0L)));

		assertEquals(ErrorCode.PROFILE_LANGUAGE_ALREADY_EXISTS, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(profileLanguages, never()).saveAndFlush(any());
	}

	@Test
	void physicallyDeletesOwnedAssociationAndReturnsVersion() {
		Profile profile = profile();
		ProfileLanguage association = profileLanguage(profile, language(22L, "English"), LanguageLevel.NATIVE);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileLanguages.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(association));
		simulateVersionIncrementOnAdvance(profile);

		ProfileVersionResponse result = service.delete(principal, 8L, 12L, 0L);

		assertEquals(1L, result.profileVersion());
		verify(profileLanguages).delete(association);
		verify(profileLanguages, never()).saveAndFlush(any());
	}

	@Test
	void rejectsForeignAssociationAndStaleProfileVersion() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileLanguages.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.empty());

		ApiException foreign = assertThrows(ApiException.class, () -> service.delete(principal, 8L, 12L, 0L));
		assertEquals(ErrorCode.PROFILE_LANGUAGE_NOT_FOUND, foreign.getErrorCode());

		ApiException stale = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileLanguageRequest(22L, "NATIVE", 1L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void translatesDatabaseDuplicateAndOptimisticLockFailures() {
		Profile profile = profile();
		Language language = language(22L, "English");
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(languages.findById(22L)).thenReturn(Optional.of(language));
		when(profileLanguages.saveAndFlush(any(ProfileLanguage.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ConstraintViolationException violation = new ConstraintViolationException("duplicate", null,
				"uk_profile_languages_profile_language");
		doThrow(new DataIntegrityViolationException("duplicate", violation)).when(profileLanguages).saveAndFlush(any());

		ApiException duplicate = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileLanguageRequest(22L, "NATIVE", 0L)));
		assertEquals(ErrorCode.PROFILE_LANGUAGE_ALREADY_EXISTS, duplicate.getErrorCode());

		org.mockito.Mockito.reset(profileLanguages, profileVersions);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(languages.findById(22L)).thenReturn(Optional.of(language));
		when(profileLanguages.saveAndFlush(any(ProfileLanguage.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doThrow(new OptimisticLockException()).when(profileVersions).advance(any());

		ApiException conflict = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileLanguageRequest(22L, "NATIVE", 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
	}

	@Test
	void returnsProfileNotFoundForMissingActiveOwnerProfile() {
		when(profileAccess.resolve(principal, 8L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
				ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

		ApiException exception = assertThrows(ApiException.class, () -> service.list(principal, 8L));

		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
		verify(profileLanguages, never()).findByProfileId(any());
	}

	private Profile profile() {
		return new Profile(user(), "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private Language language(Long id, String name) {
		Language language = new Language(name);
		ReflectionTestUtils.setField(language, "id", id);
		return language;
	}

	private ProfileLanguage profileLanguage(Profile profile, Language language, LanguageLevel level) {
		ProfileLanguage profileLanguage = new ProfileLanguage(profile, language, level);
		ReflectionTestUtils.setField(profileLanguage, "id", language.getId());
		return profileLanguage;
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
