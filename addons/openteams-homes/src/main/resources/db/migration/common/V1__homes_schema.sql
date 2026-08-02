CREATE TABLE IF NOT EXISTS oth_team_scopes (
    namespace VARCHAR(64) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    warp_count INTEGER NOT NULL,
    PRIMARY KEY (namespace, team_id)
);

CREATE TABLE IF NOT EXISTS oth_teleport_points (
    namespace VARCHAR(64) NOT NULL,
    id VARCHAR(36) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    point_type VARCHAR(16) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    normalized_name VARCHAR(64) NOT NULL,
    server_id VARCHAR(64) NOT NULL,
    world_id VARCHAR(36) NOT NULL,
    world_name VARCHAR(255) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    creator_id VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (namespace, id),
    UNIQUE (namespace, team_id, point_type, normalized_name)
);
