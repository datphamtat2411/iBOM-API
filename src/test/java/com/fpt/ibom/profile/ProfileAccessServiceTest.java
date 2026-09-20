package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileAccessServiceTest {

	private final ProfileRepository profiles = mock(ProfileRepository.class);
	private final ProfileAccessService service = new ProfileAccessService(profiles);

	@Test
	void allowsOwnersAndManagerAdminsToReadMemberProfiles() {
		Profile memberProfile = profile(8L, 7L, UserRole.MEMBER);
		when(profiles.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(memberProfile));

		assertSame(memberProfile, service.resolve(principal(7L, UserRole.MEMBER), 8L));
		assertSame(memberProfile, service.resolve(principal(20L, UserRole.MANAGER), 8L));
		assertSame(memberProfile, service.resolve(principal(21L, UserRole.ADMIN), 8L));
	}

	@Test
	void deniesNonOwnersOfManagerOrAdminProfilesWithProfileNotFound() {
		Profile managerProfile = profile(9L, 8L, UserRole.MANAGER);
		Profile adminProfile = profile(10L, 9L, UserRole.ADMIN);
		when(profiles.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(managerProfile));
		when(profiles.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(adminProfile));

		assertNotFound(() -> service.resolve(principal(20L, UserRole.MANAGER), 9L));
		assertNotFound(() -> service.resolve(principal(20L, UserRole.ADMIN), 10L));
		assertSame(managerProfile, service.resolve(principal(8L, UserRole.MANAGER), 9L));
	}

	@Test
	void mapsMissingAndSoftDeletedProfilesToTheSameNotFoundContract() {
		when(profiles.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.empty());

		ApiException missing = assertThrows(ApiException.class,
				() -> service.resolve(principal(7L, UserRole.MEMBER), 11L));
		ApiException deleted = assertThrows(ApiException.class,
				() -> service.resolve(principal(7L, UserRole.MEMBER), 11L));

		assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, missing.getErrorCode());
		assertEquals(missing.getStatus(), deleted.getStatus());
		assertEquals(missing.getErrorCode(), deleted.getErrorCode());
	}

	@Test
	void inactiveMemberProfilesRemainEligibleForAuthorizedAccess() {
		Profile profile = profile(8L, 7L, UserRole.MEMBER, UserStatus.INACTIVE);
		when(profiles.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(profile));

		assertSame(profile, service.resolve(principal(7L, UserRole.MEMBER), 8L));
		assertSame(profile, service.resolve(principal(20L, UserRole.MANAGER), 8L));
		assertSame(profile, service.resolve(principal(21L, UserRole.ADMIN), 8L));
	}

	@Test
	void resolvesOnlyAnActiveProfileOwnedByTheAuthenticatedPrincipal() {
		Profile profile = profile(8L, 7L, UserRole.MEMBER);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));

		assertSame(profile, service.resolveOwned(principal(7L, UserRole.MANAGER), 8L));
		verify(profiles).findByIdAndUserIdAndDeletedAtIsNull(8L, 7L);
		verify(profiles, never()).findByIdAndDeletedAtIsNull(8L);
	}

	@Test
	void mapsForeignAndDeletedOwnedLookupsToProfileNotFound() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(11L, 7L)).thenReturn(Optional.empty());

		assertNotFound(() -> service.resolveOwned(principal(7L, UserRole.MEMBER), 11L));
		assertNotFound(() -> service.resolveOwned(principal(7L, UserRole.MEMBER), 11L));
	}

	private void assertNotFound(Executable executable) {
		ApiException exception = assertThrows(ApiException.class, executable::run);
		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
	}

	private UserPrincipal principal(Long userId, UserRole role) {
		return new UserPrincipal(userId, "user@example.com", "user", role);
	}

	private Profile profile(Long profileId, Long userId, UserRole role) {
		return profile(profileId, userId, role, UserStatus.ACTIVE);
	}

	private Profile profile(Long profileId, Long userId, UserRole role, UserStatus status) {
		UserAccount user = new UserAccount("user" + userId + "@example.com", "user" + userId, "hash", role,
				status);
		ReflectionTestUtils.setField(user, "id", userId);
		Profile profile = new Profile(user, "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality",
				"Summary");
		ReflectionTestUtils.setField(profile, "id", profileId);
		return profile;
	}

	@FunctionalInterface
	private interface Executable {
		void run();
	}
}
