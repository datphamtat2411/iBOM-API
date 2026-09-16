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
import com.fpt.ibom.cv.service.CvExportService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.FileNameFormat;
import com.fpt.ibom.master.repository.FileNameFormatRepository;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileVersionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class CvExportIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private CvExportService exportService;

	@Autowired
	private ProfileVersionService profileVersionService;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private FileNameFormatRepository fileNameFormats;

	@Autowired
	private EntityManager entityManager;

	@Test
	void ownerCanExportPdfAndDocxAndOnlyTheRequestedProfileIsUpdated() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = saveProfile(owner, true);
		Profile otherProfile = saveProfile(owner, true);
		UserPrincipal principal = principal(owner);

		byte[] pdf = exportService.export(principal, profile.getId(), "pdf", null).bytes();
		assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));

		byte[] docx = exportService.export(principal, profile.getId(), "docx", null).bytes();
		assertEquals(0x50, docx[0]);

		Profile exported = reload(profile.getId());
		Profile untouched = reload(otherProfile.getId());
		assertTrue(exported.isHasPreviewed());
		assertTrue(exported.getLastExportedAt() != null);
		assertEquals(0L, exported.getVersion());
		assertFalse(untouched.getLastExportedAt() != null);
		assertTrue(untouched.isHasPreviewed());
	}

	@Test
	void managerAndAdminCanExportMemberProfilesButMembersCannotExportAnotherMember() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = saveProfile(owner, true);
		UserAccount manager = saveUser(UserRole.MANAGER);
		UserAccount admin = saveUser(UserRole.ADMIN);

		assertTrue(exportService.export(principal(manager), profile.getId(), "pdf", null).bytes().length > 0);
		assertTrue(exportService.export(principal(admin), profile.getId(), "pdf", null).bytes().length > 0);

		UserAccount otherMember = saveUser(UserRole.MEMBER);
		ApiException forbidden = assertThrows(ApiException.class,
				() -> exportService.export(principal(otherMember), profile.getId(), "pdf", null));
		assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
	}

	@Test
	void missingSoftDeletedAndUnpreviewedProfilesCannotGenerateDocuments() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile missing = saveProfile(owner, true);
		profileRepository.delete(missing);
		assertThrows(ApiException.class, () -> exportService.export(principal(owner), missing.getId(), "pdf", null));

		Profile deleted = saveProfile(owner, true);
		deleted.softDelete(Instant.parse("2026-01-01T00:00:00Z"));
		profileRepository.saveAndFlush(deleted);
		assertThrows(ApiException.class, () -> exportService.export(principal(owner), deleted.getId(), "pdf", null));

		Profile unpreviewed = saveProfile(owner, false);
		ApiException previewRequired = assertThrows(ApiException.class,
				() -> exportService.export(principal(owner), unpreviewed.getId(), "docx", null));
		assertEquals(ErrorCode.CV_PREVIEW_REQUIRED, previewRequired.getErrorCode());
	}

	@Test
	@Transactional
	void explicitFilenameFormatIsUsedAndStaleVersionCannotRecordExport() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = saveProfile(owner, true);
		FileNameFormat explicit = fileNameFormats.saveAndFlush(new FileNameFormat("Explicit", "Explicit-{LastName}", false));

		assertEquals("Explicit-Last.pdf", exportService.export(principal(owner), profile.getId(), "pdf",
				explicit.getId()).fileName());

		Profile stale = saveProfile(owner, true);
		long capturedVersion = stale.getVersion();
		assertEquals(1, profileRepository.advanceVersion(stale.getId(), capturedVersion));
		ApiException conflict = assertThrows(ApiException.class,
				() -> profileVersionService.markExported(stale, capturedVersion, Instant.parse("2026-01-02T00:00:00Z")));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
		assertEquals(null, reload(stale.getId()).getLastExportedAt());
	}

	private UserAccount saveUser(UserRole role) {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"user-" + UUID.randomUUID(), "hash", role, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount owner, boolean hasPreviewed) {
		Profile profile = new Profile(owner, "Profile-" + UUID.randomUUID(), "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary");
		ReflectionTestUtils.setField(profile, "hasPreviewed", hasPreviewed);
		return profileRepository.saveAndFlush(profile);
	}

	private Profile reload(Long profileId) {
		entityManager.clear();
		return profileRepository.findById(profileId).orElseThrow();
	}
	
	private UserPrincipal principal(UserAccount user) {
		return new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
	}
}
