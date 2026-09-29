package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

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
import com.fpt.ibom.cv.service.PdfRenderingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.rendering.PDFRenderer;
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
			assertTrue(text.contains("EDUCATION"));
			assertTrue(text.contains("LANGUAGES"));
			assertTrue(text.contains("CERTIFICATES"));
			assertTrue(text.contains("PROJECT EXPERIENCE"));
			assertTrue(text.contains("TECHNICAL EXPERTISE"));
			assertTrue(text.contains("FPT University"));
			assertTrue(text.contains("Ongoing"));
			assertTrue(text.contains("Completed"));
			assertFalse(text.contains("(BACKEND)"));
			assertTrue(text.contains("Backend"));
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
			assertFalse(text.contains("EDUCATION"));
			assertFalse(text.contains("LANGUAGES"));
			assertFalse(text.contains("CERTIFICATES"));
			assertFalse(text.contains("PROJECT EXPERIENCE"));
			assertFalse(text.contains("TECHNICAL EXPERTISE"));
		}
	}

	@Test
	void paginatesLongDocumentsAcrossFixedA4Pages() throws IOException {
		List<CvProject> projects = IntStream.rangeClosed(1, 30)
				.mapToObj(index -> new CvProject("Project " + index,
						"Designed and delivered reliable enterprise software for distributed teams. ".repeat(3),
						LocalDate.of(2020, 1, 1), null, CvProjectStatus.ONGOING, "Senior Engineer", 5,
						"Led implementation, testing, and production support across the project lifecycle. ".repeat(3),
						"Java, Kotlin, TypeScript", "Spring Boot, Angular, MySQL"))
				.toList();
		CvDocument longDocument = new CvDocument(
				new CvPersonalDetails("Linh", "Trần", "Senior Engineer", new BigDecimal("8.00"), "Collaborative",
						"Builds reliable software"),
				List.of(), List.of(), List.of(), projects, List.of());

		byte[] pdf = renderer.render(longDocument);

		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertTrue(document.getNumberOfPages() > 1);
			for (PDPage page : document.getPages()) {
				assertEquals(PDRectangle.A4.getWidth(), page.getMediaBox().getWidth(), 0.5f);
				assertEquals(PDRectangle.A4.getHeight(), page.getMediaBox().getHeight(), 0.5f);
			}
		}
	}

	@Test
	void firstPageFitsAtMostTwoProjectsAndSummaryBelowPersonalityPanel() throws IOException {
		CvDocument base = completeDocument();
		CvDocument cv = new CvDocument(base.personalDetails(), base.education(), base.languages(), base.certificates(),
				List.of(base.projects().get(0), base.projects().get(1), project("Third project", "Brief.")), base.skills());
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			assertEquals(2, pdf.getNumberOfPages());
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			stripper.setEndPage(1);
			String first = stripper.getText(pdf);
			assertTrue(first.replaceAll("\\s+", " ").contains("PERSONALITY / CHARACTERISTICS"));
			assertTrue(first.contains("TECHNICAL SUMMARY"));
			assertTrue(first.contains("TECHNICAL EXPERTISE"));
			assertTrue(first.contains("Cloud Migration"));
			assertTrue(first.contains("Payments Platform"));
			assertFalse(first.contains("Third project"));
			assertTrue(first.contains("Page 1 / 2"));
			stripper.setStartPage(2);
			stripper.setEndPage(2);
			String second = stripper.getText(pdf);
			assertTrue(second.contains("Third project"));
			assertFalse(second.contains("Cloud Migration"));
			assertFalse(second.contains("Payments Platform"));
			assertTrue(second.contains("Page 2 / 2"));
		}
	}

	@Test
	void longSummaryFlowsBeforeExpertiseWithoutOverlappingTheSidebar() throws IOException {
		String summary = ("Backend-focused software engineer with strong experience designing maintainable Java services, "
				+ "REST APIs and data-intensive business applications. Comfortable working across implementation, "
				+ "testing and production hardening, with a consistent focus on clear domain boundaries and reliable delivery. ").repeat(2);
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null, null, summary),
				List.of(new CvEducation("Sidebar school", "Bachelor", "Computing", LocalDate.of(2020, 1, 1), null,
						CvEducationStatus.ONGOING)), List.of(), List.of(), List.of(),
				List.of(new CvSkill("Summary skill", "BACKEND", "Backend", BigDecimal.ONE, null)));
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("reliable delivery"));
			assertTrue(text.contains("Summary skill"));
			assertTrue(text.contains("Sidebar school"));
			assertTrue(text.indexOf("TECHNICAL SUMMARY") < text.indexOf("TECHNICAL EXPERTISE"));
		}
	}

	@Test
	void expertiseStartsAtSummaryPositionWhenSummaryIsMissing() throws IOException {
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null, null, null),
				List.of(), List.of(), List.of(), List.of(),
				List.of(new CvSkill("Visible skill", "BACKEND", "Backend", BigDecimal.ONE, null)));
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			final float[] expertiseY = {-1};
			PDFTextStripper positions = new PDFTextStripper() {
				@Override
				protected void writeString(String text, List<TextPosition> characters) throws IOException {
					if (text.contains("TECHNICAL EXPERTISE")) expertiseY[0] = characters.get(0).getYDirAdj();
					super.writeString(text, characters);
				}
			};
			positions.getText(pdf);
			assertTrue(expertiseY[0] > 234 * PDRectangle.A4.getHeight() / 1123);
			assertTrue(expertiseY[0] < 300 * PDRectangle.A4.getHeight() / 1123);
		}
	}

	@Test
	void limitsOnlyThePrintedSidebarToFirstThreeEducationsAndFiveLanguagesAndCertificates() throws IOException {
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null, null, null),
				IntStream.rangeClosed(1, 4).mapToObj(i -> new CvEducation("School " + i, null, null,
						LocalDate.of(2020, 1, 1), null, CvEducationStatus.ONGOING)).toList(),
					IntStream.rangeClosed(1, 6).mapToObj(i -> new CvLanguage("Language " + i, CvLanguageLevel.ADVANCED)).toList(),
					IntStream.rangeClosed(1, 6).mapToObj(i -> new CvCertificate("Certificate " + i, LocalDate.of(2020, 1, 1))).toList(),
					List.of(), List.of());
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(text.contains("School 3"));
			assertFalse(text.contains("School 4"));
			assertTrue(text.contains("Language 5"));
			assertFalse(text.contains("Language 6"));
			assertTrue(text.contains("Certificate 5"));
			assertFalse(text.contains("Certificate 6"));
		}
	}

	@Test
	void displaysOnlySkillsThatFitPageOneAndMovesProjectsToPageTwo() throws IOException {
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null, null,
				"Summary before skills"), List.of(), List.of(), List.of(),
				List.of(project("Next page project", "Brief.")),
				IntStream.rangeClosed(1, 120).mapToObj(i -> new CvSkill("UniqueSkill" + i, "BACKEND", "Backend",
						BigDecimal.ONE, null)).toList());
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			assertEquals(2, pdf.getNumberOfPages());
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			stripper.setEndPage(1);
			String first = stripper.getText(pdf);
			assertTrue(first.contains("UniqueSkill1"));
			assertFalse(first.contains("UniqueSkill120"));
			assertFalse(first.contains("Next page project"));
			stripper.setStartPage(2);
			stripper.setEndPage(2);
			String second = stripper.getText(pdf);
			assertTrue(second.contains("Next page project"));
			assertFalse(second.contains("UniqueSkill120"));
		}
	}

	@Test
	void brandedFrameIsVisibleAndSummaryStaysBelowPanelEvenWithoutPersonality() throws IOException {
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null,
				null, "Builds reliable software"), List.of(), List.of(), List.of(), List.of(), List.of());
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			var page = new PDFRenderer(pdf).renderImage(0);
			var navy = new java.awt.Color(page.getRGB(60, 100));
			assertTrue(navy.getBlue() > navy.getRed() + 40, "First-page SVG frame should be visible");
			final float[] summaryY = {-1};
			PDFTextStripper positions = new PDFTextStripper() {
				@Override
				protected void writeString(String text, List<TextPosition> characters) throws IOException {
					if (text.contains("TECHNICAL SUMMARY")) summaryY[0] = characters.get(0).getYDirAdj();
					super.writeString(text, characters);
				}
			};
			positions.getText(pdf);
			assertTrue(summaryY[0] > 234 * PDRectangle.A4.getHeight() / 1123,
					"Technical Summary must start below the entire gray panel");
		}
	}

	@Test
	void continuationHeaderRepeatsAndShortProjectsSharePage() throws IOException {
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null,
				"Collaborative", "Builds reliable software"), List.of(), List.of(), List.of(),
				IntStream.rangeClosed(1, 18).mapToObj(index -> project("Project " + index, "Brief.")).toList(), List.of());
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			assertTrue(pdf.getNumberOfPages() >= 3);
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(2);
			stripper.setEndPage(2);
			String pageTwo = stripper.getText(pdf);
			assertTrue(pageTwo.contains("Project 3"));
			assertTrue(pageTwo.contains("Project 3"));
			assertTrue(pageTwo.contains("Project 4"));
			for (int page = 2; page <= pdf.getNumberOfPages(); page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				assertTrue(stripper.getText(pdf).contains("Linh Trần"), "Missing continuation header on page " + page);
			}
		}
	}

	@Test
	void continuationGreedilyFitsShortProjectsAndMovesWholeLongProject() throws IOException {
		List<CvProject> projects = IntStream.rangeClosed(1, 9).mapToObj(index -> project("Project " + index,
				index == 8 ? "Long description. ".repeat(120) : "Brief description.")).toList();
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null,
				"Collaborative", "Builds reliable software"), List.of(), List.of(), List.of(), projects, List.of());
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			assertTrue(pdf.getNumberOfPages() >= 3);
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(2);
			stripper.setEndPage(2);
			String second = stripper.getText(pdf);
			assertTrue(second.contains("Project 3"));
			assertTrue(second.contains("Project 3"));
			assertFalse(second.contains("Project 8"));
			stripper.setStartPage(3);
			stripper.setEndPage(pdf.getNumberOfPages());
			assertTrue(stripper.getText(pdf).contains("Project 8"));
			assertTrue(stripper.getText(pdf).contains("Project 9"));
		}
	}

	@Test
	void rejectsAProjectThatCannotFitOnAnEntireContinuationPage() {
		String ending = "Unmistakable ending of the oversized project";
		CvProject oversized = project("Oversized project", "Repeated project detail. ".repeat(650) + ending);
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Engineer", null,
				"Collaborative", "Builds reliable software"), List.of(), List.of(), List.of(),
				List.of(project("First project", "Brief."), oversized), List.of());
		assertTrue(assertThrows(PdfRenderingException.class, () -> renderer.render(cv))
				.getMessage().contains("Oversized project"));
	}

	@Test
	void movesFirstProjectWholeToPageTwoWhenItCannotFitOnPageOne() throws IOException {
		CvDocument cv = new CvDocument(new CvPersonalDetails("Linh", "Trần", "Senior Engineer",
				new BigDecimal("5.50"), "Collaborative", "Builds reliable software"),
				List.of(), List.of(), List.of(), List.of(project("First project", "Detailed description. ".repeat(125))),
				IntStream.range(0, 12).mapToObj(index -> new CvSkill("Skill " + index,
						"BACKEND", "Backend Engineering", new BigDecimal("3.00"), LocalDate.of(2026, 1, 1))).toList());
		try (PDDocument pdf = Loader.loadPDF(renderer.render(cv))) {
			assertEquals(2, pdf.getNumberOfPages());
			PDFTextStripper text = new PDFTextStripper();
			text.setStartPage(1);
			text.setEndPage(1);
			assertFalse(text.getText(pdf).contains("First project"));
			text.setStartPage(2);
			text.setEndPage(2);
			assertTrue(text.getText(pdf).contains("First project"));
		}
	}

	private CvProject project(String name, String description) {
		return new CvProject(name, description, LocalDate.of(2020, 1, 1), null, CvProjectStatus.ONGOING,
				"Engineer", 5, "Delivered the implementation", "Java", "Spring Boot");
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
