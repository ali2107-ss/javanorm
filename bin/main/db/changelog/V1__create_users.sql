--liquibase formatted sql

--changeset normacontrol:V1__create_users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    display_name VARCHAR(100) NOT NULL,
    oauth_provider VARCHAR(50),
    oauth_provider_id VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT false,
    account_locked BOOLEAN NOT NULL DEFAULT false,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    lock_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_oauth ON users(oauth_provider, oauth_provider_id);
