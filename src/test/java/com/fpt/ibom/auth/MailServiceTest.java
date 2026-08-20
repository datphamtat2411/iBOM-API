package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fpt.ibom.auth.service.MailService;
import com.fpt.ibom.config.AsyncConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class MailServiceTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final MailService asyncMailService = mock(MailService.class);
	private final MailService mailService = new MailService(mailSender, asyncMailService);

	@Test
	void skipsSmtpDeliveryForIneligiblePasswordResetRecipient() {
		mailService.deliverPasswordResetCode("unknown@example.com", "123456", false);

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void catchesSmtpFailureWithoutAffectingCompletedCaller() {
		doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

		assertDoesNotThrow(() -> mailService.deliverPasswordResetCode("user@example.com", "123456", true));
	}

	@Test
	void catchesExecutorRejectionOutsideAuthenticationServices() {
		doThrow(new TaskRejectedException("mail executor is full")).when(asyncMailService)
				.deliverPasswordResetCode(eq("user@example.com"), eq("123456"), eq(true));

		assertDoesNotThrow(() -> mailService.sendPasswordResetCode("user@example.com", "123456", true));
	}

	@Test
	void configuresBoundedDedicatedMailExecutor() {
		ThreadPoolTaskExecutor executor = new AsyncConfig(2, 4, 10).mailTaskExecutor();
		executor.initialize();
		try {
			assertEquals(2, executor.getCorePoolSize());
			assertEquals(4, executor.getMaxPoolSize());
			assertEquals(10, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
		} finally {
			executor.shutdown();
		}
	}
}
