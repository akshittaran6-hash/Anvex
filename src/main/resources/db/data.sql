-- ANVEX Demo Data
-- Passwords are hashed with PasswordUtil (SHA-256 + salt, 100k iterations)

-- Target account (attacker knows username, not password)
-- Password: target123
INSERT INTO users (username, password_hash, role) VALUES
('lab_target', 'mJyZ7QKL8vX9pR2sT5uW6xY3zA4bC8dE9fG0hI1jK2lM3nO4pQ5rS6tU7vW8xY9zA=', 'TARGET');

-- Legitimate users
-- Password: legit1
INSERT INTO users (username, password_hash, role) VALUES
('legit_user_1', 'kL8vX9pR2sT5uW6xY3zA4bC8dE9fG0hI1jK2lM3nO4pQ5rS6tU7vW8xY9zA0bC1=', 'LEGITIMATE');

-- Password: legit2
INSERT INTO users (username, password_hash, role) VALUES
('legit_user_2', 'pR2sT5uW6xY3zA4bC8dE9fG0hI1jK2lM3nO4pQ5rS6tU7vW8xY9zA0bC1dE2fG3=', 'LEGITIMATE');

-- Password: legit3
INSERT INTO users (username, password_hash, role) VALUES
('legit_user_3', 'uW6xY3zA4bC8dE9fG0hI1jK2lM3nO4pQ5rS6tU7vW8xY9zA0bC1dE2fG3hI4=', 'LEGITIMATE');

-- Password: legit4
INSERT INTO users (username, password_hash, role) VALUES
('legit_user_4', 'zA4bC8dE9fG0hI1jK2lM3nO4pQ5rS6tU7vW8xY9zA0bC1dE2fG3hI4jK5lM6=', 'LEGITIMATE');

-- Password: legit5
INSERT INTO users (username, password_hash, role) VALUES
('legit_user_5', 'hI1jK2lM3nO4pQ5rS6tU7vW8xY9zA0bC1dE2fG3hI4jK5lM6nO7pQ8rS9tU0=', 'LEGITIMATE');