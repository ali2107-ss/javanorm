--liquibase formatted sql

--changeset normacontrol:V9__alter_audit_logs_add_user_agent
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS user_agent TEXT;
