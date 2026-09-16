package com.fpt.ibom.cv.model;

public enum DocumentFormat {
	PDF(".pdf"),
	DOCX(".docx");

	private final String extension;

	DocumentFormat(String extension) {
		this.extension = extension;
	}

	public String getExtension() {
		return extension;
	}
}
