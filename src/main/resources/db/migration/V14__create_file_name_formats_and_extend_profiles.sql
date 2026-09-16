CREATE TABLE file_name_formats (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    pattern VARCHAR(1000) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    default_key BIGINT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_name_formats_default (default_key),
    CONSTRAINT chk_file_name_formats_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0 AND name = TRIM(name)),
    CONSTRAINT chk_file_name_formats_pattern_not_blank CHECK (CHAR_LENGTH(TRIM(pattern)) > 0 AND pattern = TRIM(pattern))
);

INSERT INTO file_name_formats (name, pattern, is_default, created_at, updated_at)
VALUES ('System Default', '{LastName}_{FirstName}_{Role}_{Date}', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

ALTER TABLE profiles
    ADD COLUMN last_exported_at TIMESTAMP(6) NULL,
    ADD COLUMN preferred_file_name_format_id BIGINT NULL,
    ADD CONSTRAINT fk_profiles_preferred_file_name_format
        FOREIGN KEY (preferred_file_name_format_id) REFERENCES file_name_formats (id);
