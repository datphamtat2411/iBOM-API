package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fpt.ibom.cv.model.CvCertificate;
import com.fpt.ibom.cv.model.CvDocument;
import com.fpt.ibom.cv.model.CvEducation;
import com.fpt.ibom.cv.model.CvEducationStatus;
import com.fpt.ibom.cv.model.CvLanguage;
import com.fpt.ibom.cv.model.CvLanguageLevel;
import com.fpt.ibom.cv.model.CvPersonalDetails;
import com.fpt.ibom.cv.model.CvProject;
import com.fpt.ibom.cv.model.CvProjectStatus;
import com.fpt.ibom.cv.model.CvSkill;
import com.fpt.ibom.cv.renderer.CvDocxRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class CvDocxRendererTest {

	private final CvDocxRenderer renderer = new CvDocxRenderer();

	@Test
	void rendersCanonicalContentOrderingAndUnicodeIntoReopenableDocx() throws IOException {
		CvDocument document = new CvDocument(
				new CvPersonalDetails("Nguyễn", "Thị Ánh", "Kỹ sư phần mềm", new BigDecimal("7.50"),
						"Tư duy sáng tạo", "Xây dựng nền tảng đáng tin cậy"),
				List.of(new CvEducation("Đại học FPT", "Cử nhân", "Kỹ thuật phần mềm", LocalDate.of(2018, 9, 1),
						LocalDate.of(2022, 6, 1), CvEducationStatus.COMPLETED),
						new CvEducation("Cloud Academy", "Chứng chỉ", "Điện toán đám mây", LocalDate.of(2024, 1, 1), null,
								CvEducationStatus.ONGOING)),
				List.of(new CvLanguage("Tiếng Việt", CvLanguageLevel.NATIVE),
						new CvLanguage("English", CvLanguageLevel.ADVANCED)),
				List.of(new CvCertificate("AWS Solutions Architect", LocalDate.of(2023, 5, 1)),
						new CvCertificate("CKA", LocalDate.of(2025, 2, 1))),
				List.of(new CvProject("Nền tảng hiện tại", "Xây dựng hệ thống tuyển dụng", LocalDate.of(2025, 1, 1), null,
						CvProjectStatus.ONGOING, "Tech Lead", 4, "Thiết kế dịch vụ", "Java, SQL", "Spring Boot"),
						new CvProject("Nền tảng cũ", "Bảo trì hệ thống", LocalDate.of(2021, 1, 1), LocalDate.of(2023, 12, 1),
								CvProjectStatus.COMPLETED, "Engineer", 3, "Sửa lỗi", "Java", "Docker")),
				List.of(new CvSkill("Java", "BACKEND", "Backend", new BigDecimal("3.50"), LocalDate.of(2025, 8, 1)),
						new CvSkill("SQL", "DATABASE", "Database", new BigDecimal("2.25"), null)));

		byte[] bytes = renderer.render(document);

		assertTrue(bytes.length > 0);
		try (XWPFDocument reopened = new XWPFDocument(new ByteArrayInputStream(bytes))) {
			List<String> paragraphs = reopened.getParagraphs().stream().map(paragraph -> paragraph.getText()).toList();
			String content = paragraphs.stream().reduce("", (left, right) ->
					left + "\n" + right);

			assertContainsAll(content, "Nguyễn Thị Ánh", "Kỹ sư phần mềm", "7.50", "Tư duy sáng tạo",
					"Xây dựng nền tảng đáng tin cậy", "Đại học FPT", "Kỹ thuật phần mềm", "01 Sep 2018", "Completed",
					"Cloud Academy", "Ongoing", "Tiếng Việt", "Native", "English", "Advanced", "AWS Solutions Architect",
					"01 May 2023", "Nền tảng hiện tại", "Xây dựng hệ thống tuyển dụng", "Tech Lead", "Team size: 4",
					"Thiết kế dịch vụ", "Java, SQL", "Spring Boot", "Java", "Backend", "3.50", "01 Aug 2025", "SQL",
					"Database", "2.25");
			assertTrue(content.indexOf("Nền tảng hiện tại") < content.indexOf("Nền tảng cũ"));
			assertTrue(paragraphs.indexOf("Java") < paragraphs.indexOf("SQL"));
		}
	}

	@Test
	void omitsEmptyCollectionSectionsAndTheirHeadings() throws IOException {
		CvDocument document = new CvDocument(
				new CvPersonalDetails("First", "Last", "Engineer", null, null, null), List.of(), List.of(), List.of(),
						List.of(), List.of());

		byte[] bytes = renderer.render(document);

		assertFalse(bytes.length == 0);
		try (XWPFDocument reopened = new XWPFDocument(new ByteArrayInputStream(bytes))) {
			String content = reopened.getParagraphs().stream().map(paragraph -> paragraph.getText()).reduce("", (left, right) ->
					left + "\n" + right);

			assertContainsAll(content, "First Last", "Engineer");
			assertFalse(content.contains("Education"));
			assertFalse(content.contains("Languages"));
			assertFalse(content.contains("Certificates"));
			assertFalse(content.contains("Projects"));
			assertFalse(content.contains("Skills"));
		}
	}

	private void assertContainsAll(String content, String... expectedValues) {
		for (String expectedValue : expectedValues) {
			assertTrue(content.contains(expectedValue), () -> "Missing DOCX content: " + expectedValue);
		}
	}
}
