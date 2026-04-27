--liquibase formatted sql

--changeset normacontrol:V3__create_check_results
CREATE TABLE check_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id),
    rule_set_name VARCHAR(100) NOT NULL,
    rule_set_version VARCHAR(20),
    compliance_score INT NOT NULL DEFAULT 0,
    passed BOOLEAN NOT NULL DEFAULT false,
    report_storage_path VARCHAR(1000),
    processing_time_ms BIGINT,
    checked_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_check_results_document
    ON check_results(document_id, checked_at DESC);
CREATE INDEX idx_check_results_score
    ON check_results(compliance_score);
