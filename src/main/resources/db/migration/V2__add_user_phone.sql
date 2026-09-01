ALTER TABLE users
    ADD COLUMN phone_number VARCHAR(16);

ALTER TABLE users
    ADD CONSTRAINT uk_users_phone_number UNIQUE (phone_number);
