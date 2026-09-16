package com.fpt.ibom.cv.model;

import java.util.List;

public record CvDocument(CvPersonalDetails personalDetails, List<CvEducation> education, List<CvLanguage> languages,
		List<CvCertificate> certificates, List<CvProject> projects, List<CvSkill> skills) {

	public CvDocument {
		education = List.copyOf(education);
		languages = List.copyOf(languages);
		certificates = List.copyOf(certificates);
		projects = List.copyOf(projects);
		skills = List.copyOf(skills);
	}
}
