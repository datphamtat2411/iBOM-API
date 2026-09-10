CREATE TABLE certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    certificate_name VARCHAR(255) NOT NULL,
    issue_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_certificates_profile_issue_date_id (profile_id, issue_date, id),
    UNIQUE KEY uk_certificates_profile_name_issue_date (profile_id, certificate_name, issue_date),
    CONSTRAINT fk_certificates_profile FOREIGN KEY (profile_id) REFERENCES profiles (id)
);
