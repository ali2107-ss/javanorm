--liquibase formatted sql

--changeset normacontrol:V6__insert_default_rule_set
CREATE TABLE rule_sets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    version VARCHAR(20) NOT NULL,
    description TEXT,
    rules JSONB NOT NULL DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO rule_sets (name, version, description, rules) VALUES (
    'ГОСТ 19.201-78',
    '1.0',
    'Техническое задание. Требования к содержанию и оформлению.',
    '["STRUCTURE","FORMATTING","TABLES","FIGURES","LANGUAGE"]'
);
