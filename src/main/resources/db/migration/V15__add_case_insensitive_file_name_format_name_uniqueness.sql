ALTER TABLE file_name_formats
    ADD COLUMN name_ci VARCHAR(255) GENERATED ALWAYS AS (LOWER(name)) STORED,
    ADD UNIQUE KEY uk_file_name_formats_name_ci (name_ci);
