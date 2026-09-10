CREATE TABLE profile_languages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    language_id BIGINT NOT NULL,
    level VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_profile_languages_profile_id (profile_id),
    KEY idx_profile_languages_language_id (language_id),
    UNIQUE KEY uk_profile_languages_profile_language (profile_id, language_id),
    CONSTRAINT fk_profile_languages_profile FOREIGN KEY (profile_id) REFERENCES profiles (id),
    CONSTRAINT fk_profile_languages_language FOREIGN KEY (language_id) REFERENCES languages (id),
    CONSTRAINT chk_profile_languages_level CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'UPPER_INTERMEDIATE', 'ADVANCED', 'NATIVE'))
);
