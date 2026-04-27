--liquibase formatted sql

--changeset normacontrol:V2__create_documents
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_file_name VARCHAR(500) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    file_size_bytes BIGINT,
    owner_id UUID NOT NULL REFERENCES users(id),
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_documents_owner_status
    ON documents(owner_id, status) WHERE deleted = false;
CREATE INDEX idx_documents_created_at
    ON documents(created_at DESC);
