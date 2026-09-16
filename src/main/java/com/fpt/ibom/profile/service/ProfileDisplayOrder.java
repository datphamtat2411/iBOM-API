package com.fpt.ibom.profile.service;

import java.util.Comparator;

import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;

public final class ProfileDisplayOrder {

	private ProfileDisplayOrder() {
	}

	public static Comparator<ProfileLanguage> languageComparator() {
		return Comparator.comparingInt((ProfileLanguage profileLanguage) -> proficiencyRank(profileLanguage.getLevel()))
				.thenComparing(ProfileLanguage::getLanguage,
						Comparator.comparing(Language::getName, String.CASE_INSENSITIVE_ORDER))
				.thenComparing(ProfileLanguage::getId, Comparator.nullsLast(Comparator.naturalOrder()));
	}

	public static Comparator<ProfileSkill> skillComparator() {
		return Comparator.comparing(ProfileSkill::getExperienceYears, Comparator.reverseOrder())
				.thenComparing(profileSkill -> profileSkill.getSkill().getName(), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(ProfileSkill::getId, Comparator.nullsLast(Comparator.naturalOrder()));
	}

	private static int proficiencyRank(LanguageLevel level) {
		return switch (level) {
		case NATIVE -> 0;
		case ADVANCED -> 1;
		case UPPER_INTERMEDIATE -> 2;
		case INTERMEDIATE -> 3;
		case BEGINNER -> 4;
		};
	}
}
