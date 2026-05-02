--liquibase formatted sql

--changeset normacontrol:V4__create_violations
CREATE TABLE violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_result_id UUID NOT NULL REFERENCES check_results(id) ON DELETE CASCADE,
    rule_code VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    page_number INT,
    line_number INT,
    suggestion TEXT,
    ai_suggestion TEXT,
    rule_reference VARCHAR(200)
);

CREATE INDEX idx_violations_check_result
    ON violations(check_result_id);
CREATE INDEX idx_violations_severity
    ON violations(check_result_id, severity);
