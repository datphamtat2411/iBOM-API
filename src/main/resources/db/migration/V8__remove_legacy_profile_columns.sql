ALTER TABLE profiles
    MODIFY COLUMN first_name VARCHAR(100) NOT NULL,
    MODIFY COLUMN last_name VARCHAR(100) NOT NULL,
    MODIFY COLUMN job_title VARCHAR(100) NOT NULL,
    MODIFY COLUMN personality VARCHAR(4000) NOT NULL,
    MODIFY COLUMN technical_summary VARCHAR(4000) NOT NULL,
    DROP COLUMN full_name,
    DROP COLUMN email,
    DROP COLUMN phone_number,
    DROP COLUMN address;
