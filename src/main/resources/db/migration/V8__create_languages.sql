CREATE TABLE languages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    name_ci VARCHAR(255) GENERATED ALWAYS AS (LOWER(name)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_languages_name_ci (name_ci),
    CONSTRAINT chk_languages_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);

INSERT INTO languages (name, created_at, updated_at)
VALUES
    ('English', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('Vietnamese', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('Japanese', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
