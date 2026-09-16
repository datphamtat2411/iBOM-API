package com.fpt.ibom.cv.service;

public class PdfRenderingException extends RuntimeException {

	public PdfRenderingException(String message) {
		super(message);
	}

	public PdfRenderingException(String message, Throwable cause) {
		super(message, cause);
	}
}
