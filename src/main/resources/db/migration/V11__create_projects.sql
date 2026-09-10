CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    position VARCHAR(255) NOT NULL,
    team_size INT NOT NULL,
    responsibilities TEXT NOT NULL,
    programming_languages TEXT NULL,
    tools TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_projects_profile_status_end_date_start_date_created_at
        (profile_id, status, end_date, start_date, created_at),
    CONSTRAINT fk_projects_profile FOREIGN KEY (profile_id) REFERENCES profiles (id),
    CONSTRAINT chk_projects_status CHECK (status IN ('ONGOING', 'COMPLETED')),
    CONSTRAINT chk_projects_team_size CHECK (team_size >= 1),
    CONSTRAINT chk_projects_dates CHECK (
        (status = 'ONGOING' AND end_date IS NULL)
        OR (status = 'COMPLETED' AND end_date IS NOT NULL AND start_date <= end_date)
    )
);
