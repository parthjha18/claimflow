-- ClaimFlow V1 schema. Hibernate runs with ddl-auto=validate, so this file is the source of truth.
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()

CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(100) NOT NULL,
    full_name        VARCHAR(200) NOT NULL,
    role             VARCHAR(20)  NOT NULL CHECK (role IN ('EMPLOYEE','MANAGER','FINANCE','ADMIN')),
    manager_id       UUID REFERENCES users(id),
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT chk_not_own_manager CHECK (manager_id IS NULL OR manager_id <> id)
);
CREATE INDEX idx_users_org     ON users(organization_id);
CREATE INDEX idx_users_manager ON users(manager_id);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,      -- SHA-256 of the token, never the raw token
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);

-- One policy per organisation; per-category limits are child rows.
CREATE TABLE expense_policies (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL UNIQUE REFERENCES organizations(id),
    per_claim_max           NUMERIC(14,2) NOT NULL CHECK (per_claim_max > 0),
    receipt_required_above  NUMERIC(14,2) NOT NULL CHECK (receipt_required_above >= 0),
    finance_approval_above  NUMERIC(14,2) NOT NULL CHECK (finance_approval_above >= 0),
    escalation_after_days   INT NOT NULL DEFAULT 3 CHECK (escalation_after_days > 0),
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID
);

CREATE TABLE policy_category_limits (
    policy_id   UUID NOT NULL REFERENCES expense_policies(id) ON DELETE CASCADE,
    category    VARCHAR(20) NOT NULL CHECK (category IN ('TRAVEL','FOOD','LODGING','OTHER')),
    max_amount  NUMERIC(14,2) NOT NULL CHECK (max_amount > 0),
    PRIMARY KEY (policy_id, category)
);

CREATE TABLE claims (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    claimant_id          UUID NOT NULL REFERENCES users(id),
    title                VARCHAR(200) NOT NULL,
    description          TEXT,
    total_amount         NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    currency             CHAR(3) NOT NULL DEFAULT 'INR',
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','MANAGER_APPROVED','FINANCE_APPROVED','PAID','REJECTED')),
    requires_finance     BOOLEAN NOT NULL DEFAULT FALSE,   -- decided at submit time from the policy
    current_approver_id  UUID REFERENCES users(id),        -- manager who must act; moved by escalation
    submitted_at         TIMESTAMPTZ,
    status_changed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),  -- drives the escalation job
    version              BIGINT NOT NULL DEFAULT 0,           -- @Version optimistic lock
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID
);
CREATE INDEX idx_claims_org_status ON claims(organization_id, status);
CREATE INDEX idx_claims_claimant   ON claims(claimant_id, created_at DESC);
CREATE INDEX idx_claims_approver   ON claims(current_approver_id, status);
CREATE INDEX idx_claims_escalation ON claims(status, status_changed_at)
    WHERE status IN ('SUBMITTED','MANAGER_APPROVED');

CREATE TABLE claim_line_items (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id              UUID NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    organization_id       UUID NOT NULL REFERENCES organizations(id),  -- denormalised for reports
    category              VARCHAR(20) NOT NULL CHECK (category IN ('TRAVEL','FOOD','LODGING','OTHER')),
    amount                NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    expense_date          DATE NOT NULL,
    description           VARCHAR(500),
    receipt_ref           VARCHAR(500),      -- opaque key issued by StorageService
    receipt_filename      VARCHAR(255),
    receipt_content_type  VARCHAR(100),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_items_claim      ON claim_line_items(claim_id);
CREATE INDEX idx_items_org_report ON claim_line_items(organization_id, expense_date, category);

CREATE TABLE approval_steps (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id     UUID NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    actor_id     UUID NOT NULL REFERENCES users(id),
    action       VARCHAR(20) NOT NULL
        CHECK (action IN ('SUBMIT','APPROVE','REJECT','REQUEST_CHANGES','PAY','ESCALATE')),
    from_status  VARCHAR(20) NOT NULL,
    to_status    VARCHAR(20) NOT NULL,
    comment      VARCHAR(1000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_steps_claim ON approval_steps(claim_id, created_at);

CREATE TABLE audit_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    action           VARCHAR(50) NOT NULL,
    actor_id         UUID,                     -- NULL means the system (scheduler)
    old_value        JSONB,
    new_value        JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, created_at);
CREATE INDEX idx_audit_org    ON audit_logs(organization_id, created_at DESC);

CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id),
    idem_key         VARCHAR(100) NOT NULL,
    endpoint         VARCHAR(200) NOT NULL,    -- method + path template + resource id
    request_hash     VARCHAR(64)  NOT NULL,    -- same key + different request => 422
    state            VARCHAR(12)  NOT NULL CHECK (state IN ('IN_PROGRESS','COMPLETED')),
    response_status  INT,
    response_body    JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, idem_key, endpoint)       -- the unique constraint is what makes races safe
);
