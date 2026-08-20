ALTER TABLE verification_codes
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
