package com.fpt.ibom.cv.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fpt.ibom.cv.model.CvDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Service
public class CvPdfRenderer {

	private static final Duration BROWSER_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern ERROR = Pattern.compile("data-cv-error=\"([^\"]*)\"");
	private static final Pattern PROJECT_INDEX = Pattern.compile("data-cv-project-index=\"(\\d+)\"");
	private final TemplateEngine templateEngine;
	private final CvPrintFormat display = new CvPrintFormat();
	private final String styles;
	private final String paginationScript;

	public CvPdfRenderer() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode("HTML");
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(false);
		templateEngine = new TemplateEngine();
		templateEngine.setTemplateResolver(resolver);
		try {
			styles = readResource("templates/cv/cv.css");
			paginationScript = readResource("templates/cv/paginate.js");
		} catch (IOException exception) {
			throw new PdfRenderingException("Missing CV print template resource", exception);
		}
	}

	public byte[] render(CvDocument document) {
		Objects.requireNonNull(document, "document must not be null");
		Context context = new Context();
		context.setVariable("document", document);
		context.setVariable("display", display);
		context.setVariable("styles", styles);
		context.setVariable("paginationScript", paginationScript);
		String html = templateEngine.process("cv/cv", context);

		Path temporary = null;
		try {
			temporary = Files.createTempDirectory("cv-print-");
			Path input = temporary.resolve("cv.html");
			Path output = temporary.resolve("cv.pdf");
			Files.writeString(input, html, StandardCharsets.UTF_8);
			String url = input.toUri().toString();
			String layout = browser(temporary, url, "--dump-dom");
			Matcher error = ERROR.matcher(layout);
			if (!error.find()) throw new PdfRenderingException("CV pagination did not complete");
			if (!error.group(1).isEmpty()) throw layoutError(document, layout, error.group(1));
			browser(temporary, url, "--no-pdf-header-footer", "--print-to-pdf=" + output);
			byte[] bytes = Files.readAllBytes(output);
			if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
				throw new PdfRenderingException("PDF rendering produced invalid output");
			}
			return bytes;
		} catch (PdfRenderingException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new PdfRenderingException("Unable to render CV document as PDF", exception);
		} finally {
			deleteQuietly(temporary);
		}
	}

	private PdfRenderingException layoutError(CvDocument document, String layout, String error) {
		if (error.equals("PROJECT_TOO_LARGE")) {
			Matcher index = PROJECT_INDEX.matcher(layout);
			if (index.find()) {
				int position = Integer.parseInt(index.group(1));
				if (position < document.projects().size()) {
					return new PdfRenderingException("Project cannot fit on one CV page: " + document.projects().get(position).name());
				}
			}
		}
		return new PdfRenderingException("CV content does not fit the print layout: " + error);
	}

	private String browser(Path temporary, String url, String... mode) throws Exception {
		String binary = System.getProperty("cv.pdf.chrome",
				System.getenv().getOrDefault("CV_PDF_CHROME", "google-chrome"));
		List<String> command = new ArrayList<>(List.of(binary, "--headless", "--no-sandbox", "--disable-gpu",
				"--disable-dev-shm-usage", "--disable-extensions", "--no-first-run", "--no-default-browser-check",
				"--virtual-time-budget=10000", "--user-data-dir=" + temporary.resolve("browser-profile")));
		command.addAll(List.of(mode));
		command.add(url);
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException exception) {
				throw new PdfRenderingException("Unable to read Chromium output", exception);
			}
		});
		try {
			if (!process.waitFor(BROWSER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				throw new PdfRenderingException("Chromium timed out while rendering CV");
			}
			String text = output.get(BROWSER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			if (process.exitValue() != 0) throw new PdfRenderingException("Chromium failed to render CV: " + text);
			return text;
		} finally {
			if (process.isAlive()) process.destroyForcibly();
		}
	}

	private String readResource(String resource) throws IOException {
		try (var stream = new ClassPathResource(resource).getInputStream()) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private void deleteQuietly(Path directory) {
		if (directory == null) return;
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// A temporary browser file may still be closing; never hide the render result.
				}
			});
		} catch (IOException ignored) {
			// Temporary cleanup must not replace the rendering exception.
		}
	}
}
