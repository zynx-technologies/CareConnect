-- CareConnect's own sign-in accounts, used where Entra ID is not (dev and test). Production uses Entra ID.
-- Roles are a comma-separated list of the same role names Entra app roles use.
CREATE TABLE app_user (
    id                   VARCHAR(36)   NOT NULL PRIMARY KEY,
    username             NVARCHAR(100) NOT NULL,
    display_name         NVARCHAR(200) NOT NULL,
    password_hash        NVARCHAR(200) NOT NULL,
    roles                NVARCHAR(200) NOT NULL,
    enabled              INT           NOT NULL DEFAULT 1,
    must_change_password INT           NOT NULL DEFAULT 1,
    failed_attempts      INT           NOT NULL DEFAULT 0,
    locked_until         DATETIME2     NULL,
    created_at           DATETIME2     NOT NULL,
    created_by           NVARCHAR(200) NOT NULL,
    password_changed_at  DATETIME2     NULL,
    last_login_at        DATETIME2     NULL,
    CONSTRAINT uq_app_user_username UNIQUE (username)
);
