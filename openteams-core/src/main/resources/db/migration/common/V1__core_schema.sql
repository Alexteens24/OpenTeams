CREATE TABLE teams (
    namespace VARCHAR(64) NOT NULL,
    id VARCHAR(36) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    normalized_name VARCHAR(64) NOT NULL,
    tag VARCHAR(16),
    normalized_tag VARCHAR(16),
    owner_id VARCHAR(36) NOT NULL,
    state VARCHAR(16) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    member_limit INTEGER NOT NULL,
    version BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    PRIMARY KEY (namespace, id)
);

CREATE TABLE team_name_claims (
    namespace VARCHAR(64) NOT NULL,
    normalized_name VARCHAR(64) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (namespace, normalized_name),
    UNIQUE (namespace, team_id),
    FOREIGN KEY (namespace, team_id) REFERENCES teams(namespace, id)
        ON DELETE CASCADE
);

CREATE TABLE team_tag_claims (
    namespace VARCHAR(64) NOT NULL,
    normalized_tag VARCHAR(16) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (namespace, normalized_tag),
    UNIQUE (namespace, team_id),
    FOREIGN KEY (namespace, team_id) REFERENCES teams(namespace, id)
        ON DELETE CASCADE
);

CREATE TABLE role_templates (
    namespace VARCHAR(64) NOT NULL,
    role_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    priority INTEGER NOT NULL,
    member_limit INTEGER,
    protected_role INTEGER NOT NULL,
    PRIMARY KEY (namespace, role_key)
);

CREATE TABLE role_permissions (
    namespace VARCHAR(64) NOT NULL,
    role_key VARCHAR(64) NOT NULL,
    permission_key VARCHAR(128) NOT NULL,
    PRIMARY KEY (namespace, role_key, permission_key),
    FOREIGN KEY (namespace, role_key) REFERENCES role_templates(namespace, role_key)
        ON DELETE CASCADE
);

-- The default namespace roles are inserted by Core after migration because
-- namespace is a runtime configuration value. Core only seeds them when the
-- namespace has no role definitions, so administrator customizations persist.

CREATE TABLE team_members (
    namespace VARCHAR(64) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    role_key VARCHAR(64) NOT NULL,
    joined_at BIGINT NOT NULL,
    last_active_at BIGINT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (namespace, player_id),
    FOREIGN KEY (namespace, team_id) REFERENCES teams(namespace, id)
        ON DELETE CASCADE,
    FOREIGN KEY (namespace, role_key) REFERENCES role_templates(namespace, role_key)
        ON DELETE RESTRICT
);

CREATE INDEX idx_team_members_team ON team_members(namespace, team_id);

CREATE TABLE team_invitations (
    namespace VARCHAR(64) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    inviter_id VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (namespace, team_id, target_id),
    FOREIGN KEY (namespace, team_id) REFERENCES teams(namespace, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_team_invitations_target ON team_invitations(namespace, target_id);

CREATE TABLE team_join_requests (
    namespace VARCHAR(64) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (namespace, team_id, player_id),
    FOREIGN KEY (namespace, team_id) REFERENCES teams(namespace, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_team_join_requests_expiry
    ON team_join_requests(namespace, expires_at);

CREATE TABLE team_bans (
    namespace VARCHAR(64) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    reason VARCHAR(255),
    created_at BIGINT NOT NULL,
    expires_at BIGINT,
    PRIMARY KEY (namespace, team_id, player_id),
    FOREIGN KEY (namespace, team_id) REFERENCES teams(namespace, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_team_bans_expiry ON team_bans(namespace, expires_at);

CREATE TABLE team_settings (
    namespace VARCHAR(64) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (namespace, team_id, setting_key),
    FOREIGN KEY (namespace, team_id) REFERENCES teams(namespace, id)
        ON DELETE CASCADE
);

CREATE TABLE player_preferences (
    namespace VARCHAR(64) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    team_chat INTEGER NOT NULL,
    staff_spy INTEGER NOT NULL,
    locale_override VARCHAR(32),
    PRIMARY KEY (namespace, player_id)
);

CREATE TABLE player_directory (
    namespace VARCHAR(64) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    last_known_name VARCHAR(16) NOT NULL,
    normalized_name VARCHAR(16) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (namespace, player_id)
);

CREATE INDEX idx_player_directory_name
    ON player_directory(namespace, normalized_name);

CREATE INDEX idx_teams_public_name
    ON teams(namespace, visibility, state, normalized_name);

CREATE TABLE audit_entries (
    namespace VARCHAR(64) NOT NULL,
    id VARCHAR(36) NOT NULL,
    team_id VARCHAR(36),
    actor_id VARCHAR(36),
    action VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    metadata_json TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (namespace, id)
);

CREATE INDEX idx_audit_team_time ON audit_entries(namespace, team_id, created_at);
CREATE INDEX idx_audit_actor_time ON audit_entries(namespace, actor_id, created_at);

CREATE TABLE core_lease_fences (
    namespace VARCHAR(64) NOT NULL,
    next_token BIGINT NOT NULL,
    PRIMARY KEY (namespace)
);

CREATE TABLE core_leases (
    namespace VARCHAR(64) NOT NULL,
    instance_id VARCHAR(36) NOT NULL,
    fence_token BIGINT NOT NULL,
    validation_counter BIGINT NOT NULL,
    heartbeat_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (namespace)
);
