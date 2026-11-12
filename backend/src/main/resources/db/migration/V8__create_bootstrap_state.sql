CREATE TABLE bootstrap_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    initialized BOOLEAN NOT NULL DEFAULT FALSE,
    initialized_at TIMESTAMPTZ
);

INSERT INTO bootstrap_state (id, initialized)
VALUES (1, FALSE);