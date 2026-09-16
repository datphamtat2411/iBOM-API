package com.fpt.ibom.cv.renderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fpt.ibom.cv.model.CvCertificate;
import com.fpt.ibom.cv.model.CvDocument;
import com.fpt.ibom.cv.model.CvEducation;
import com.fpt.ibom.cv.model.CvLanguage;
import com.fpt.ibom.cv.model.CvPersonalDetails;
import com.fpt.ibom.cv.model.CvProject;
import com.fpt.ibom.cv.model.CvSkill;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

@Component
public class CvDocxRenderer {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	public byte[] render(CvDocument document) {
		Objects.requireNonNull(document, "document must not be null");

		try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			renderPersonalDetails(docx, document.personalDetails());
			renderEducation(docx, document);
			renderLanguages(docx, document);
			renderCertificates(docx, document);
			renderProjects(docx, document);
			renderSkills(docx, document);
			docx.write(output);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to render CV document as DOCX", exception);
		}
	}

	private void renderPersonalDetails(XWPFDocument document, CvPersonalDetails details) {
		addParagraph(document, "Curriculum Vitae", true, 20, null, ParagraphAlignment.CENTER);
		if (details == null) {
			return;
		}

		addParagraph(document, joinNonBlank(details.firstName(), details.lastName()), true, 16, null,
				ParagraphAlignment.CENTER);
		addField(document, "Job title", details.jobTitle());
		addField(document, "Years of experience", details.yearsOfExperience());
		addField(document, "Personality", details.personality());
		addField(document, "Technical summary", details.technicalSummary());
	}

	private void renderEducation(XWPFDocument document, CvDocument cvDocument) {
		if (cvDocument.education().isEmpty()) {
			return;
		}

		addHeading(document, "Education");
		for (CvEducation education : cvDocument.education()) {
			addItemHeading(document, education.schoolName());
			addField(document, "Degree", education.degree());
			addField(document, "Field of study", education.fieldOfStudy());
			addField(document, "Start date", education.startDate());
			addField(document, "End date", education.endDate());
			addField(document, "Status", humanize(education.status()));
		}
	}

	private void renderLanguages(XWPFDocument document, CvDocument cvDocument) {
		if (cvDocument.languages().isEmpty()) {
			return;
		}

		addHeading(document, "Languages");
		for (CvLanguage language : cvDocument.languages()) {
			addBullet(document, joinNonBlank(language.languageName(), humanize(language.level())));
		}
	}

	private void renderCertificates(XWPFDocument document, CvDocument cvDocument) {
		if (cvDocument.certificates().isEmpty()) {
			return;
		}

		addHeading(document, "Certificates");
		for (CvCertificate certificate : cvDocument.certificates()) {
			addItemHeading(document, certificate.certificateName());
			addField(document, "Issue date", certificate.issueDate());
		}
	}

	private void renderProjects(XWPFDocument document, CvDocument cvDocument) {
		if (cvDocument.projects().isEmpty()) {
			return;
		}

		addHeading(document, "Projects");
		for (CvProject project : cvDocument.projects()) {
			addItemHeading(document, project.name());
			addField(document, "Description", project.description());
			addField(document, "Start date", project.startDate());
			addField(document, "End date", project.endDate());
			addField(document, "Status", humanize(project.status()));
			addField(document, "Position", project.position());
			addField(document, "Team size", project.teamSize());
			addField(document, "Responsibilities", project.responsibilities());
			addField(document, "Programming languages", project.programmingLanguages());
			addField(document, "Tools", project.tools());
		}
	}

	private void renderSkills(XWPFDocument document, CvDocument cvDocument) {
		if (cvDocument.skills().isEmpty()) {
			return;
		}

		addHeading(document, "Skills");
		for (CvSkill skill : cvDocument.skills()) {
			addItemHeading(document, skill.skillName());
			addField(document, "Category", joinNonBlank(skill.categoryCode(), skill.categoryName()));
			addField(document, "Experience", skill.experienceYears());
			addField(document, "Last used", skill.lastUsed());
		}
	}

	private void addHeading(XWPFDocument document, String text) {
		addParagraph(document, text, true, 14, "Heading1", ParagraphAlignment.LEFT);
	}

	private void addItemHeading(XWPFDocument document, String text) {
		if (isBlank(text)) {
			return;
		}
		addParagraph(document, text, true, 11, null, ParagraphAlignment.LEFT);
	}

	private void addField(XWPFDocument document, String label, Object value) {
		if (value == null || (value instanceof String string && isBlank(string))) {
			return;
		}

		XWPFParagraph paragraph = document.createParagraph();
		paragraph.setSpacingAfter(80);
		XWPFRun labelRun = paragraph.createRun();
		labelRun.setBold(true);
		labelRun.setFontFamily("Arial");
		labelRun.setFontSize(10);
		labelRun.setText(label + ": ");
		XWPFRun valueRun = paragraph.createRun();
		valueRun.setFontFamily("Arial");
		valueRun.setFontSize(10);
		valueRun.setText(format(value));
	}

	private void addBullet(XWPFDocument document, String text) {
		if (isBlank(text)) {
			return;
		}
		addParagraph(document, text, false, 10, "List Bullet", ParagraphAlignment.LEFT);
	}

	private XWPFParagraph addParagraph(XWPFDocument document, String text, boolean bold, int fontSize, String style,
			ParagraphAlignment alignment) {
		XWPFParagraph paragraph = document.createParagraph();
		if (style != null) {
			paragraph.setStyle(style);
		}
		paragraph.setAlignment(alignment);
		paragraph.setSpacingAfter(120);
		if (!isBlank(text)) {
			XWPFRun run = paragraph.createRun();
			run.setBold(bold);
			run.setFontFamily("Arial");
			run.setFontSize(fontSize);
			run.setText(text);
		}
		return paragraph;
	}

	private String format(Object value) {
		if (value instanceof LocalDate date) {
			return DATE_FORMAT.format(date);
		}
		if (value instanceof BigDecimal decimal) {
			return decimal.toPlainString();
		}
		return value.toString();
	}

	private String humanize(Enum<?> value) {
		if (value == null) {
			return null;
		}
		String lowerCase = value.name().toLowerCase(Locale.ENGLISH);
		return Stream.of(lowerCase.split("_"))
				.map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
				.collect(Collectors.joining(" "));
	}

	private String joinNonBlank(String first, String second) {
		return Stream.of(first, second).filter(value -> !isBlank(value)).collect(Collectors.joining(" "));
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
