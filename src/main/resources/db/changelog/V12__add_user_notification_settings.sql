--liquibase formatted sql

--changeset normacontrol:V12__add_user_notification_settings
ALTER TABLE users
    ADD COLUMN email_reports_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN gost_updates_enabled BOOLEAN NOT NULL DEFAULT false;
