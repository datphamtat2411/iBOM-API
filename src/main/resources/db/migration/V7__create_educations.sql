CREATE TABLE educations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    school_name VARCHAR(255) NOT NULL,
    degree VARCHAR(255) NOT NULL,
    field_of_study VARCHAR(255) NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_educations_profile_id (profile_id),
    CONSTRAINT fk_educations_profile FOREIGN KEY (profile_id) REFERENCES profiles (id),
    CONSTRAINT chk_educations_status CHECK (status IN ('ONGOING', 'COMPLETED'))
);
