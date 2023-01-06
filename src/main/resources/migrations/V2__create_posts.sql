CREATE TABLE posts (
    id uuid PRIMARY KEY,
    title text NOT NULL,
    user_id uuid NOT NULL REFERENCES users(id)
);
