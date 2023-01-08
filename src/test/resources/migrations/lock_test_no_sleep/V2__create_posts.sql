CREATE TABLE posts (
    id uuid PRIMARY KEY,
    title text,
    user_id uuid REFERENCES users(id)
);