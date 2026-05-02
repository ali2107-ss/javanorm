--liquibase formatted sql

--changeset normacontrol:V1_1__create_user_roles
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
