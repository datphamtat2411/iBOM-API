package com.fpt.ibom.cv.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.fpt.ibom.cv.model.CvPersonalDetails;
import com.fpt.ibom.cv.model.CvSkill;

/** Presentation-only formatting for the CV print template; persisted values remain untouched. */
public class CvPrintFormat {

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
	private static final Pattern SEPARATOR = Pattern.compile("\\s*[,·]\\s*");

	public String name(CvPersonalDetails details) {
		return (details.firstName() + " " + details.lastName()).trim();
	}

	public String years(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString() + "+";
	}

	public String skillYears(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString() + " yrs";
	}

	public String month(LocalDate date) {
		return date == null ? "Present" : MONTH.format(date);
	}

	public String year(LocalDate date) {
		return date == null ? "" : Integer.toString(date.getYear());
	}

	public String label(Enum<?> value) {
		if (value == null) return "";
		String[] words = value.name().toLowerCase(Locale.ENGLISH).split("_");
		StringBuilder label = new StringBuilder();
		for (String word : words) {
			if (!label.isEmpty()) label.append(' ');
			label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return label.toString();
	}

	public Map<String, List<CvSkill>> skillGroups(List<CvSkill> skills) {
		Map<String, List<CvSkill>> groups = new LinkedHashMap<>();
		for (CvSkill skill : skills) {
			String category = skill.categoryName() == null || skill.categoryName().isBlank()
					? "Other Skills" : skill.categoryName();
			groups.computeIfAbsent(category, ignored -> new ArrayList<>()).add(skill);
		}
		return groups;
	}

	public List<String> responsibilities(String text) {
		if (text == null || text.isBlank()) return List.of();
		return text.lines().map(String::trim).filter(line -> !line.isEmpty())
				.map(line -> line.replaceFirst("^[•\\-*]\\s*", "")).toList();
	}

	public String separated(String text) {
		return text == null ? "" : SEPARATOR.matcher(text.trim()).replaceAll(" · ");
	}
}
