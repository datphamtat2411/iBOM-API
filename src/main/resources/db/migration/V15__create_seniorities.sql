CREATE TABLE seniorities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    from_experience DECIMAL(5,2) NOT NULL,
    to_experience DECIMAL(5,2) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    name_ci VARCHAR(255) GENERATED ALWAYS AS (LOWER(name)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seniorities_name_ci (name_ci),
    CONSTRAINT chk_seniorities_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0 AND name = TRIM(name)),
    CONSTRAINT chk_seniorities_from_experience_nonnegative CHECK (from_experience >= 0),
    CONSTRAINT chk_seniorities_to_experience_valid CHECK (to_experience IS NULL OR to_experience > from_experience)
);
