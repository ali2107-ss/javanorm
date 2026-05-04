--liquibase formatted sql

--changeset normacontrol:V14__enable_pgcrypto
CREATE EXTENSION IF NOT EXISTS pgcrypto;

COMMENT ON EXTENSION pgcrypto IS 'Required by NormaControl security requirements for production encryption and hashing of sensitive fields';
