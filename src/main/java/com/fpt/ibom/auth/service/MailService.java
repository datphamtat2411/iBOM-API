package com.fpt.ibom.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);
	private final JavaMailSender mailSender;
	private final MailService asyncMailService;

	public MailService(JavaMailSender mailSender, @Lazy MailService asyncMailService) {
		this.mailSender = mailSender;
		this.asyncMailService = asyncMailService;
	}

	public void sendPasswordResetCode(String email, String code, boolean eligibleForDelivery) {
		submit(() -> asyncMailService.deliverPasswordResetCode(email, code, eligibleForDelivery));
	}

	public void sendRegistrationVerificationCode(String email, String code) {
		submit(() -> asyncMailService.deliverRegistrationVerificationCode(email, code));
	}

	@Async("mailTaskExecutor")
	public void deliverPasswordResetCode(String email, String code, boolean eligibleForDelivery) {
		if (!eligibleForDelivery) {
			return;
		}
		deliver(email, "iBOM password reset verification code",
				"Your password reset verification code is: " + code + ". It expires in 5 minutes.");
	}

	@Async("mailTaskExecutor")
	public void deliverRegistrationVerificationCode(String email, String code) {
		deliver(email, "iBOM registration verification code",
				"Your registration verification code is: " + code + ". It expires in 5 minutes.");
	}

	private void submit(Runnable task) {
		try {
			task.run();
		} catch (TaskRejectedException exception) {
			LOGGER.warn("Mail delivery task was rejected", exception);
		}
	}

	private void deliver(String email, String subject, String text) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setTo(email);
			message.setSubject(subject);
			message.setText(text);
			mailSender.send(message);
		} catch (MailException exception) {
			LOGGER.warn("Mail delivery failed for recipient {}", email, exception);
		}
	}
}
