-- V1__baseline.sql
-- Baseline schema for document approval workflow engine

-- Flow definitions (data-defined templates)
CREATE TABLE flow_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    initial_state VARCHAR(255) NOT NULL,
    initiator_group_id VARCHAR(255) NOT NULL,
    states JSONB NOT NULL,
    transitions JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (key, version)
);

-- Flow instances (running flows)
CREATE TABLE flow_instance (
    id UUID PRIMARY KEY,
    definition_id UUID NOT NULL REFERENCES flow_definition(id),
    definition_key VARCHAR(255) NOT NULL,
    definition_version INT NOT NULL,
    document_ref VARCHAR(500) NOT NULL,
    submitter_id VARCHAR(255) NOT NULL,
    current_state VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    terminal_outcome VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_instance_status ON flow_instance(status);
CREATE INDEX idx_flow_instance_definition_key ON flow_instance(definition_key);

-- Human tasks
CREATE TABLE task (
    id UUID PRIMARY KEY,
    flow_instance_id UUID NOT NULL REFERENCES flow_instance(id),
    state_name VARCHAR(255) NOT NULL,
    candidate_group_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    owner_id VARCHAR(255),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_task_status ON task(status);
CREATE INDEX idx_task_candidate_group ON task(candidate_group_id, status);
CREATE INDEX idx_task_owner ON task(owner_id, status);
CREATE INDEX idx_task_flow_instance ON task(flow_instance_id);

-- Decisions
CREATE TABLE decision (
    task_id UUID PRIMARY KEY REFERENCES task(id),
    outcome VARCHAR(32) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    comment TEXT,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Persons (minimal actor reference)
CREATE TABLE person (
    id VARCHAR(255) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL
);

-- Groups
CREATE TABLE "group" (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

-- Group membership (many-to-many)
CREATE TABLE group_membership (
    person_id VARCHAR(255) NOT NULL REFERENCES person(id),
    group_id VARCHAR(255) NOT NULL REFERENCES "group"(id),
    PRIMARY KEY (person_id, group_id)
);

CREATE INDEX idx_group_membership_group ON group_membership(group_id);

-- Audit log (append-only — Principle III)
CREATE TABLE audit_entry (
    id BIGSERIAL PRIMARY KEY,
    flow_instance_id UUID NOT NULL REFERENCES flow_instance(id),
    task_id UUID REFERENCES task(id),
    type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(255),
    payload JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entry_flow_instance ON audit_entry(flow_instance_id, id);

-- Transactional outbox (Principle V)
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    flow_instance_id UUID NOT NULL REFERENCES flow_instance(id),
    type VARCHAR(255) NOT NULL,
    data JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    dispatched_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_event_status ON outbox_event(status);
