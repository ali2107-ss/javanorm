--liquibase formatted sql

--changeset normacontrol:V7__create_check_strategy_settings
CREATE TABLE check_strategy_settings (
    strategy_code VARCHAR(32) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT true
);

INSERT INTO check_strategy_settings(strategy_code, enabled) VALUES
('STRUCT', true),
('FMT', true),
('TBL', true),
('FIG', true),
('LANG', true),
('REF', true);
