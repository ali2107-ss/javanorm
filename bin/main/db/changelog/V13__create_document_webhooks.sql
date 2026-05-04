--liquibase formatted sql

--changeset normacontrol:V13__create_document_webhooks
CREATE TABLE document_webhooks (
    document_id UUID PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id),
    callback_url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
