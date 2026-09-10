CREATE TABLE skill_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_categories_code (code),
    UNIQUE KEY uk_skill_categories_name (name)
);

CREATE TABLE skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    name_ci VARCHAR(255) GENERATED ALWAYS AS (LOWER(name)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skills_name_ci (name_ci),
    KEY idx_skills_category_id (category_id),
    CONSTRAINT fk_skills_category FOREIGN KEY (category_id) REFERENCES skill_categories (id),
    CONSTRAINT chk_skills_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0 AND name = TRIM(name))
);

INSERT INTO skill_categories (code, name)
VALUES
    ('PROGRAMMING_LANGUAGE', 'Programming Language'),
    ('FRONTEND', 'Frontend'),
    ('BACKEND', 'Backend'),
    ('MOBILE_GAME', 'Mobile & Game'),
    ('DATABASE_DATA', 'Database & Data'),
    ('CLOUD_DEVOPS', 'Cloud & DevOps'),
    ('API_MESSAGING_TESTING', 'API, Messaging & Testing');
