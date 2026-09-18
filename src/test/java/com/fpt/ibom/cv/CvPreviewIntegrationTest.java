package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.cv.service.CvPreviewService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

@SpringBootTest
class CvPreviewIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private CvPreviewService previewService;

	@Autowired
	private ProfileVersionService profileVersionService;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void previewGeneratesPdfMarksOnlyTheRequestedProfileAndPreservesExportTimestamp() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = saveProfile(owner, false);
		Profile otherProfile = saveProfile(owner, false);
		Instant exportedAt = Instant.parse("2026-02-03T04:05:06Z");
		profile.markExportedAt(exportedAt);
		profile = profileRepository.saveAndFlush(profile);
		long capturedVersion = profile.getVersion();

		byte[] pdf = previewService.preview(new UserPrincipal(owner.getId(), owner.getEmail(), owner.getUsername(),
				UserRole.MEMBER), profile.getId());

		assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
		Profile previewed = profileRepository.findById(profile.getId()).orElseThrow();
		Profile untouched = profileRepository.findById(otherProfile.getId()).orElseThrow();
		assertTrue(previewed.isHasPreviewed());
		assertEquals(capturedVersion, previewed.getVersion());
		assertEquals(exportedAt, previewed.getLastExportedAt());
		assertFalse(untouched.isHasPreviewed());
	}

	@Test
	void managerAndAdminCanPreviewActiveAndInactiveMemberProfilesButNotManagementProfiles() {
		UserAccount activeMember = saveUser(UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount inactiveMember = saveUser(UserRole.MEMBER, UserStatus.INACTIVE);
		UserAccount manager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		UserAccount admin = saveUser(UserRole.ADMIN, UserStatus.ACTIVE);
		UserAccount otherManager = saveUser(UserRole.MANAGER, UserStatus.ACTIVE);
		UserAccount otherAdmin = saveUser(UserRole.ADMIN, UserStatus.ACTIVE);
		Profile activeProfile = saveProfile(activeMember, false);
		Profile inactiveProfile = saveProfile(inactiveMember, false);
		Profile managerProfile = saveProfile(otherManager, false);
		Profile adminProfile = saveProfile(otherAdmin, false);

		assertPdf(previewService.preview(principal(manager), activeProfile.getId()));
		assertPdf(previewService.preview(principal(admin), inactiveProfile.getId()));

		assertNotFound(() -> previewService.preview(principal(manager), managerProfile.getId()));
		assertNotFound(() -> previewService.preview(principal(admin), adminProfile.getId()));
	}

	@Test
	void missingUnauthorizedSoftDeletedAndManagementOwnedProfilesHaveTheSameNotFoundContract() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		UserAccount foreignMember = saveUser(UserRole.MEMBER);
		UserAccount manager = saveUser(UserRole.MANAGER);
		Profile foreignProfile = saveProfile(owner, false);
		Profile deletedProfile = saveProfile(owner, false);
		deletedProfile.softDelete(Instant.parse("2026-01-01T00:00:00Z"));
		profileRepository.saveAndFlush(deletedProfile);
		Profile managementProfile = saveProfile(manager, false);

		ApiException unauthorized = assertThrows(ApiException.class,
				() -> previewService.preview(principal(foreignMember), foreignProfile.getId()));
		ApiException missing = assertThrows(ApiException.class,
				() -> previewService.preview(principal(owner), Long.MAX_VALUE));
		ApiException deleted = assertThrows(ApiException.class,
				() -> previewService.preview(principal(owner), deletedProfile.getId()));
		ApiException management = assertThrows(ApiException.class,
				() -> previewService.preview(principal(foreignMember), managementProfile.getId()));

		assertNotFound(unauthorized);
		assertNotFound(missing);
		assertNotFound(deleted);
		assertNotFound(management);
	}

	@Test
	@Transactional
	void conditionalPreviewMarkRejectsAStaleAssembledVersion() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = saveProfile(owner, false);

		assertEquals(1, profileRepository.advanceVersion(profile.getId(), 0L));
		ApiException exception = assertThrows(ApiException.class,
				() -> profileVersionService.markPreviewed(profile, 0L));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		entityManager.clear();
		Profile current = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(1L, current.getVersion());
		assertFalse(current.isHasPreviewed());
	}

	private UserAccount saveUser(UserRole role) {
		return saveUser(role, UserStatus.ACTIVE);
	}

	private UserAccount saveUser(UserRole role, UserStatus status) {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"user-" + UUID.randomUUID(), "hash", role, status));
	}

	private Profile saveProfile(UserAccount owner, boolean hasPreviewed) {
		Profile profile = new Profile(owner, "Profile-" + UUID.randomUUID(), "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary");
		ReflectionTestUtils.setField(profile, "hasPreviewed", hasPreviewed);
		return profileRepository.saveAndFlush(profile);
	}

	private UserPrincipal principal(UserAccount user) {
		return new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
	}

	private void assertPdf(byte[] pdf) {
		assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
	}

	private void assertNotFound(ApiException exception) {
		assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
	}

	private void assertNotFound(Executable executable) {
		assertNotFound(assertThrows(ApiException.class, executable::run));
	}

	@FunctionalInterface
	private interface Executable {
		void run();
	}
}
