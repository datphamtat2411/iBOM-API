package com.fpt.ibom.cv.service;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.service.ProfileVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CvPreviewService {

	private final CvProfileAccessService profileAccessService;
	private final CvDocumentAssembler documentAssembler;
	private final CvPdfRenderer pdfRenderer;
	private final ProfileVersionService profileVersionService;

	public CvPreviewService(CvProfileAccessService profileAccessService, CvDocumentAssembler documentAssembler,
			CvPdfRenderer pdfRenderer, ProfileVersionService profileVersionService) {
		this.profileAccessService = profileAccessService;
		this.documentAssembler = documentAssembler;
		this.pdfRenderer = pdfRenderer;
		this.profileVersionService = profileVersionService;
	}

	@Transactional
	public byte[] preview(UserPrincipal principal, Long profileId) {
		Profile profile = profileAccessService.resolve(principal, profileId);
		long capturedVersion = profile.getVersion();
		var document = documentAssembler.assemble(profileId);
		byte[] pdf = pdfRenderer.render(document);
		profileVersionService.markPreviewed(profile, capturedVersion);
		return pdf;
	}
}
