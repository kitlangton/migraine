-- migraine snapshot
-- v5
-- v1 .. v5 on a test container
-- dump the schema
-- V5_SNAPSHOT.sql
CREATE TABLE users
(
    id    uuid PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL
);

CREATE TABLE posts
(
    id      uuid PRIMARY KEY,
    title   text,
    user_id uuid REFERENCES users (id)
);