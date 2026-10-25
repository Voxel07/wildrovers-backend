-- Apply BEFORE deploying the backend with HttpOnly cookie sessions.
-- MariaDB 10.5+. Safe to re-run. Production uses schema validation, so the
-- table must exist before the new backend starts.
CREATE TABLE IF NOT EXISTS AUTH_SESSION (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    previous_token_hash VARCHAR(64) NULL,
    rotated_at BIGINT NULL,
    token_version INT NOT NULL,
    login_method VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    last_used_at BIGINT NOT NULL,
    revoked_at BIGINT NULL,
    idp_refresh_token_encrypted VARCHAR(4096) NULL,
    idp_access_expires_at BIGINT NULL,
    user_agent VARCHAR(255) NULL,
    UNIQUE KEY ix_auth_session_token (token_hash),
    KEY ix_auth_session_previous (previous_token_hash),
    KEY ix_auth_session_user (user_id)
);
