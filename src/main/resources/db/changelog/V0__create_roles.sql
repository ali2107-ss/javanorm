--liquibase formatted sql

--changeset normacontrol:V0__create_roles
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO roles (name) VALUES ('ROLE_REVIEWER');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');
