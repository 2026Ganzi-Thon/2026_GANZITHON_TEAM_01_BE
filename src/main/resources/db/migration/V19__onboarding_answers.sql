CREATE TABLE onboarding_answers (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    answers TEXT NOT NULL
);
