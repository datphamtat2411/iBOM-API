CREATE TABLE seniority_mutation_lock (
    id TINYINT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO seniority_mutation_lock (id) VALUES (1);
