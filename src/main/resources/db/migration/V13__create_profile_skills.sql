CREATE TABLE profile_skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    experience_years DECIMAL(5,2) NOT NULL,
    last_used DATE NULL,
    PRIMARY KEY (id),
    KEY idx_profile_skills_profile_id (profile_id),
    KEY idx_profile_skills_skill_id (skill_id),
    UNIQUE KEY uk_profile_skills_profile_skill (profile_id, skill_id),
    CONSTRAINT fk_profile_skills_profile FOREIGN KEY (profile_id) REFERENCES profiles (id),
    CONSTRAINT fk_profile_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id),
    CONSTRAINT chk_profile_skills_experience_years CHECK (experience_years >= 0)
);
