package com.fpt.ibom.cv.service;

import java.time.Clock;
import java.time.Instant;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.cv.model.CvExportResult;
import com.fpt.ibom.cv.model.DocumentFormat;
import com.fpt.ibom.cv.renderer.CvDocxRenderer;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.service.ProfileVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CvExportService {

	private final CvProfileAccessService profileAccessService;
	private final FileNameResolutionService fileNameResolutionService;
	private final CvDocumentAssembler documentAssembler;
	private final CvPdfRenderer pdfRenderer;
	private final CvDocxRenderer docxRenderer;
	private final ProfileVersionService profileVersionService;
	private final Clock clock;

	public CvExportService(CvProfileAccessService profileAccessService,
			FileNameResolutionService fileNameResolutionService, CvDocumentAssembler documentAssembler,
			CvPdfRenderer pdfRenderer, CvDocxRenderer docxRenderer, ProfileVersionService profileVersionService,
			Clock clock) {
		this.profileAccessService = profileAccessService;
		this.fileNameResolutionService = fileNameResolutionService;
		this.documentAssembler = documentAssembler;
		this.pdfRenderer = pdfRenderer;
		this.docxRenderer = docxRenderer;
		this.profileVersionService = profileVersionService;
		this.clock = clock;
	}

	public CvExportResult export(UserPrincipal principal, Long profileId, String requestedFormat,
			Long explicitFileNameFormatId) {
		Profile profile = profileAccessService.resolve(principal, profileId);
		if (!profile.isHasPreviewed()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CV_PREVIEW_REQUIRED,
					"Profile must be previewed before export");
		}

		long capturedVersion = profile.getVersion();
		DocumentFormat documentFormat = DocumentFormat.fromRequest(requestedFormat);
		Instant exportTimestamp = clock.instant();
		String fileName = fileNameResolutionService.resolve(profile, explicitFileNameFormatId, documentFormat,
				exportTimestamp);
		var document = documentAssembler.assemble(profileId);
		byte[] bytes = documentFormat == DocumentFormat.PDF ? pdfRenderer.render(document) : docxRenderer.render(document);
		profileVersionService.markExported(profile, capturedVersion, exportTimestamp);
		return new CvExportResult(bytes, fileName, requestedFormat.trim().toLowerCase(java.util.Locale.ROOT));
	}
}
