package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.dto.CertificateRequest;
import com.fpt.ibom.profile.dto.ProfileLanguageRequest;
import com.fpt.ibom.profile.dto.ProfileSkillRequest;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.service.CertificateService;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import com.fpt.ibom.profile.service.ProfileSkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ProfileChildConstraintIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private CertificateService certificateService;
	@Autowired
	private ProfileLanguageService profileLanguageService;
	@Autowired
	private ProfileSkillService profileSkillService;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@MockitoSpyBean
	private CertificateRepository certificateRepository;
	@MockitoSpyBean
	private ProfileLanguageRepository profileLanguageRepository;
	@MockitoSpyBean
	private ProfileSkillRepository profileSkillRepository;
	@Autowired
	private LanguageRepository languageRepository;
	@Autowired
	private SkillCategoryRepository skillCategoryRepository;
	@Autowired
	private SkillRepository skillRepository;

	@Test
	void certificateCreateConstraintIsTranslatedAndRollsBackAggregateAdvancement() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);
		LocalDate issueDate = LocalDate.of(2024, 1, 1);
		certificateRepository.saveAndFlush(new Certificate(profile, "AWS", issueDate));
		doReturn(false).when(certificateRepository)
				.existsByProfileIdAndCertificateNameAndIssueDate(profile.getId(), "AWS", issueDate);

		ApiException exception = assertThrows(ApiException.class, () -> certificateService.create(user.getId(), profile.getId(),
				new CertificateRequest("AWS", issueDate, 0L)));

		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, exception.getErrorCode());
		assertAggregateRolledBack(profile, true);
		assertEquals(1, certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(profile.getId()).size());
	}

	@Test
	void certificateUpdateConstraintIsTranslatedAndRollsBackAggregateAdvancement() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);
		LocalDate firstDate = LocalDate.of(2023, 1, 1);
		LocalDate duplicateDate = LocalDate.of(2024, 1, 1);
		Certificate first = certificateRepository.saveAndFlush(new Certificate(profile, "Azure", firstDate));
		certificateRepository.saveAndFlush(new Certificate(profile, "AWS", duplicateDate));
		doReturn(false).when(certificateRepository)
				.existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(profile.getId(), "AWS", duplicateDate,
						first.getId());

		ApiException exception = assertThrows(ApiException.class, () -> certificateService.update(user.getId(), profile.getId(),
				first.getId(), new CertificateRequest("AWS", duplicateDate, 0L)));

		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, exception.getErrorCode());
		assertAggregateRolledBack(profile, true);
		Certificate unchanged = certificateRepository.findById(first.getId()).orElseThrow();
		assertEquals("Azure", unchanged.getCertificateName());
		assertEquals(firstDate, unchanged.getIssueDate());
	}

	@Test
	void profileLanguageConstraintIsTranslatedAndRollsBackAggregateAdvancement() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);
		Language english = language("English");
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, english, LanguageLevel.NATIVE));
		doReturn(false).when(profileLanguageRepository).existsByProfileIdAndLanguageId(profile.getId(), english.getId());

		ApiException exception = assertThrows(ApiException.class,
				() -> profileLanguageService.create(user.getId(), profile.getId(),
						new ProfileLanguageRequest(english.getId(), "ADVANCED", 0L)));

		assertEquals(ErrorCode.PROFILE_LANGUAGE_ALREADY_EXISTS, exception.getErrorCode());
		assertAggregateRolledBack(profile, true);
		assertEquals(1, profileLanguageRepository.findByProfileId(profile.getId()).size());
	}

	@Test
	void profileSkillConstraintIsTranslatedAndRollsBackAggregateAdvancement() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);
		Skill skill = saveSkill("Constraint-Skill-" + UUID.randomUUID());
		profileSkillRepository.saveAndFlush(new ProfileSkill(profile, skill, BigDecimal.ONE, null));
		doReturn(false).when(profileSkillRepository).existsByProfileIdAndSkillId(profile.getId(), skill.getId());

		ApiException exception = assertThrows(ApiException.class,
				() -> profileSkillService.create(user.getId(), profile.getId(),
						new ProfileSkillRequest(skill.getId(), BigDecimal.TEN, null, 0L)));

		assertEquals(ErrorCode.PROFILE_SKILL_ALREADY_EXISTS, exception.getErrorCode());
		assertAggregateRolledBack(profile, true);
		assertEquals(1, profileSkillRepository.findByProfileId(profile.getId()).size());
	}

	private void assertAggregateRolledBack(Profile profile, boolean expectedHasPreviewed) {
		Profile reloaded = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(0L, reloaded.getVersion());
		assertEquals(expectedHasPreviewed, reloaded.isHasPreviewed());
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com", "member-" + UUID.randomUUID(),
				"hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, boolean hasPreviewed) {
		Profile profile = new Profile(user, "Profile-" + UUID.randomUUID(), "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary");
		ReflectionTestUtils.setField(profile, "hasPreviewed", hasPreviewed);
		return profileRepository.saveAndFlush(profile);
	}

	private Language language(String name) {
		return languageRepository.findAll().stream().filter(language -> name.equals(language.getName())).findFirst().orElseThrow();
	}

	private Skill saveSkill(String name) {
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		return skillRepository.saveAndFlush(new Skill(name, category));
	}
}
