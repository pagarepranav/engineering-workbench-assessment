CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, description VARCHAR(255) NOT NULL);
INSERT INTO schema_version(version, description) SELECT 1, 'Initial links and workflow storage' WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE version=1);
CREATE TABLE IF NOT EXISTS links (
 id VARCHAR(36) PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE,
 destination VARCHAR(4096) NOT NULL, created_at VARCHAR(40) NOT NULL,
 expires_at VARCHAR(40), active BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS click_events (
 id VARCHAR(36) PRIMARY KEY, link_id VARCHAR(36) NOT NULL REFERENCES links(id), clicked_at VARCHAR(40) NOT NULL,
 click_day VARCHAR(10) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_click_link_day ON click_events(link_id, click_day);
CREATE TABLE IF NOT EXISTS workflow_runs (
 id VARCHAR(36) PRIMARY KEY, version BIGINT NOT NULL, snapshot TEXT NOT NULL, created_at VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS workflow_revisions (
 run_id VARCHAR(36) NOT NULL REFERENCES workflow_runs(id), revision INTEGER NOT NULL, snapshot TEXT NOT NULL,
 PRIMARY KEY(run_id, revision)
);
CREATE TABLE IF NOT EXISTS audit_events (
 run_id VARCHAR(36) NOT NULL REFERENCES workflow_runs(id), sequence_no BIGINT NOT NULL,
 occurred_at VARCHAR(40) NOT NULL, event_type VARCHAR(80) NOT NULL, detail TEXT NOT NULL,
 previous_hash VARCHAR(64) NOT NULL, event_hash VARCHAR(64) NOT NULL,
 PRIMARY KEY(run_id, sequence_no)
);
