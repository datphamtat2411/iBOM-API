CREATE TABLE verification_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_verification_codes_rate_limit (email, purpose, created_at),
    KEY idx_verification_codes_lookup (email, purpose, used_at, created_at)
);

ALTER TABLE users
    DROP INDEX uk_users_email,
    DROP INDEX uk_users_username,
    ADD COLUMN email_ci VARCHAR(255) GENERATED ALWAYS AS (LOWER(email)) STORED,
    ADD COLUMN username_ci VARCHAR(100) GENERATED ALWAYS AS (LOWER(username)) STORED,
    ADD UNIQUE KEY uk_users_email_ci (email_ci),
    ADD UNIQUE KEY uk_users_username_ci (username_ci);
