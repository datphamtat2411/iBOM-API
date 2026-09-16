package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.fpt.ibom.cv.service.CvPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class CvPdfRendererTest {

	private final CvPdfRenderer renderer = new CvPdfRenderer();

	@Test
	void rendersCompleteDocumentAsValidPdfWithOrderedUnicodeContent() throws IOException {
		byte[] pdf = renderer.render(completeDocument());

		assertValidPdf(pdf);
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertTrue(document.getNumberOfPages() >= 1);
			String text = new PDFTextStripper().getText(document);
			assertTrue(text.contains("Nguyễn Ánh"));
			assertTrue(text.contains("Senior Backend Engineer"));
			assertTrue(text.contains("Education"));
			assertTrue(text.contains("Languages"));
			assertTrue(text.contains("Certificates"));
			assertTrue(text.contains("Projects"));
			assertTrue(text.contains("Skills"));
			assertTrue(text.contains("FPT University"));
			assertTrue(text.contains("ONGOING"));
			assertTrue(text.contains("COMPLETED"));
			assertTrue(text.contains("Vietnamese"));
			assertTrue(text.contains("Cloud Migration"));
			assertTrue(text.contains("AWS Solutions Architect"));
			assertTrue(text.contains("Spring Boot"));
			assertTrue(text.contains("Docker"));
		}
	}

	@Test
	void omitsEmptyCollectionSectionsAndKeepsPersonalDetails() throws IOException {
		byte[] pdf = renderer.render(new CvDocument(
				new CvPersonalDetails("Linh", "Trần", "Developer", new BigDecimal("2.50"), "Calm",
						"Builds reliable software"),
				List.of(), List.of(), List.of(), List.of(), List.of()));

		assertValidPdf(pdf);
		try (PDDocument document = Loader.loadPDF(pdf)) {
			String text = new PDFTextStripper().getText(document);
			assertTrue(text.contains("Linh Trần"));
			assertTrue(text.contains("Builds reliable software"));
			assertFalse(text.contains("Education"));
			assertFalse(text.contains("Languages"));
			assertFalse(text.contains("Certificates"));
			assertFalse(text.contains("Projects"));
			assertFalse(text.contains("Skills"));
		}
	}

	private void assertValidPdf(byte[] pdf) {
		assertTrue(pdf.length > 0);
		assertTrue(new String(pdf, 0, Math.min(pdf.length, 5), java.nio.charset.StandardCharsets.US_ASCII)
				.startsWith("%PDF-"));
	}

	private CvDocument completeDocument() {
		return new CvDocument(new CvPersonalDetails("Nguyễn", "Ánh", "Senior Backend Engineer",
				new BigDecimal("7.50"), "Curious and collaborative", "Designs scalable services"),
				List.of(new CvEducation("FPT University", "Bachelor", "Software Engineering",
						LocalDate.of(2018, 9, 1), LocalDate.of(2022, 6, 1), CvEducationStatus.COMPLETED),
						new CvEducation("Cloud Academy", "Certificate", "Cloud Computing", LocalDate.of(2025, 1, 1),
								null, CvEducationStatus.ONGOING)),
				List.of(new CvLanguage("Vietnamese", CvLanguageLevel.NATIVE),
						new CvLanguage("English", CvLanguageLevel.ADVANCED)),
				List.of(new CvCertificate("AWS Solutions Architect", LocalDate.of(2024, 5, 1))),
				List.of(new CvProject("Cloud Migration", "Migrated critical services", LocalDate.of(2023, 1, 1),
						LocalDate.of(2024, 2, 1), CvProjectStatus.COMPLETED, "Tech Lead", 5,
						"Guided the migration", "Java, Kotlin", "Docker"),
						new CvProject("Payments Platform", "Builds payment services", LocalDate.of(2024, 3, 1), null,
								CvProjectStatus.ONGOING, "Engineer", 4, "Develops APIs", "Java", "Spring Boot")),
				List.of(new CvSkill("Spring Boot", "BACKEND", "Backend", new BigDecimal("5.25"), LocalDate.of(2026, 1, 1)),
						new CvSkill("Docker", "DEVOPS", "DevOps", new BigDecimal("3.00"), null)));
	}
}
