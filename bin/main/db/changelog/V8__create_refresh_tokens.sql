--liquibase formatted sql

--changeset normacontrol:V8__create_refresh_tokens
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
