package com.fpt.ibom.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "verification_codes")
public class VerificationCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
	private String codeHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VerificationPurpose purpose;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected VerificationCode() {
	}

	public VerificationCode(String email, String codeHash, VerificationPurpose purpose, Instant expiresAt, Instant createdAt) {
		this.email = email;
		this.codeHash = codeHash;
		this.purpose = purpose;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public String getCodeHash() { return codeHash; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getUsedAt() { return usedAt; }
	public void use(Instant usedAt) { this.usedAt = usedAt; }
}
