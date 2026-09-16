package com.fpt.ibom.cv.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.fpt.ibom.cv.model.CvDocument;
import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

@Service
public class CvPdfRenderer {

	private static final String TEMPLATE = "cv/cv";
	private static final String TEMPLATE_BASE = "templates/cv/";
	private static final String REGULAR_FONT = "fonts/NotoSans-Regular.ttf";
	private static final String BOLD_FONT = "fonts/NotoSans-Bold.ttf";

	private final TemplateEngine templateEngine;
	private final String templateBaseUrl;

	public CvPdfRenderer() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode("HTML");
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(false);
		templateEngine = new TemplateEngine();
		templateEngine.setTemplateResolver(resolver);
		templateBaseUrl = resourceUrl(TEMPLATE_BASE);
	}

	public byte[] render(CvDocument document) {
		Objects.requireNonNull(document, "document must not be null");
		Path regularFont = null;
		Path boldFont = null;
		try {
			Context context = new Context();
			context.setVariable("document", document);
			String xhtml = templateEngine.process(TEMPLATE, context);

			regularFont = materializeResource(REGULAR_FONT);
			boldFont = materializeResource(BOLD_FONT);
			ITextRenderer pdfRenderer = new ITextRenderer();
			pdfRenderer.getFontResolver().addFont(regularFont.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
			pdfRenderer.getFontResolver().addFont(boldFont.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
			pdfRenderer.setDocumentFromString(xhtml, templateBaseUrl);
			pdfRenderer.layout();

			ByteArrayOutputStream output = new ByteArrayOutputStream();
			pdfRenderer.createPDF(output);
			byte[] bytes = output.toByteArray();
			if (bytes.length == 0) {
				throw new PdfRenderingException("PDF rendering produced empty output");
			}
			return bytes;
		} catch (PdfRenderingException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new PdfRenderingException("Unable to render CV document as PDF", exception);
		} finally {
			deleteQuietly(regularFont);
			deleteQuietly(boldFont);
		}
	}

	private String resourceUrl(String resourcePath) {
		try {
			return Objects.requireNonNull(getClass().getClassLoader().getResource(resourcePath), resourcePath)
					.toExternalForm();
		} catch (RuntimeException exception) {
			throw new PdfRenderingException("Missing CV rendering resource: " + resourcePath, exception);
		}
	}

	private Path materializeResource(String resourcePath) throws IOException {
		String suffix = resourcePath.substring(resourcePath.lastIndexOf('.'));
		Path temporaryFile = Files.createTempFile("cv-renderer-", suffix);
		try (InputStream input = new ClassPathResource(resourcePath).getInputStream()) {
			Files.copy(input, temporaryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return temporaryFile;
		} catch (Exception exception) {
			deleteQuietly(temporaryFile);
			throw exception;
		}
	}

	private void deleteQuietly(Path file) {
		if (file != null) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException ignored) {
				// Do not replace a successful PDF with a cleanup failure.
			}
		}
	}
}
