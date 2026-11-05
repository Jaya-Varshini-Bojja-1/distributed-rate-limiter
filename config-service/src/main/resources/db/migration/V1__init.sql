-- Configuration Service schema. The gateway does not query these tables per request.

CREATE TABLE plans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT plans_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT plans_code_unique UNIQUE (code)
);

CREATE TABLE clients (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT clients_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT clients_plan_fk FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE RESTRICT
);

-- PostgreSQL does not index foreign keys automatically.
CREATE INDEX clients_plan_id_idx ON clients (plan_id);

CREATE TABLE api_keys (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id BIGINT NOT NULL,
    key_hash CHAR(64) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    label VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT api_keys_key_hash_unique UNIQUE (key_hash),
    CONSTRAINT api_keys_client_fk FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE RESTRICT
);

CREATE INDEX api_keys_client_id_idx ON api_keys (client_id);

CREATE TABLE rate_limit_policies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    http_method VARCHAR(8) NOT NULL,
    capacity INT NOT NULL,
    refill_tokens INT NOT NULL,
    refill_period_seconds INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT rate_limit_policies_path_check CHECK (path_pattern LIKE '/%'),
    -- 'ANY' is a real value. NULL would not be equal to NULL, so UNIQUE would allow duplicates.
    CONSTRAINT rate_limit_policies_method_check CHECK (
        http_method IN ('ANY', 'GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS')
    ),
    CONSTRAINT rate_limit_policies_capacity_check CHECK (capacity BETWEEN 1 AND 1000000),
    CONSTRAINT rate_limit_policies_refill_tokens_check CHECK (refill_tokens BETWEEN 1 AND 1000000),
    CONSTRAINT rate_limit_policies_refill_period_check CHECK (refill_period_seconds BETWEEN 1 AND 86400),
    -- The default policy is the row /** + ANY. It cannot be stored as disabled.
    CONSTRAINT rate_limit_policies_default_enabled_check CHECK (
        enabled OR NOT (path_pattern = '/**' AND http_method = 'ANY')
    ),
    CONSTRAINT rate_limit_policies_plan_path_method_unique UNIQUE (plan_id, path_pattern, http_method),
    CONSTRAINT rate_limit_policies_plan_fk FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE
);

INSERT INTO plans (code, name, description)
VALUES
    ('FREE', 'Free', 'Free tier'),
    ('PRO', 'Pro', 'Pro tier'),
    ('ENTERPRISE', 'Enterprise', 'Enterprise tier');

INSERT INTO rate_limit_policies (
    plan_id, path_pattern, http_method, capacity, refill_tokens, refill_period_seconds, enabled
)
SELECT id, '/**', 'ANY', 20, 100, 60, TRUE
FROM plans
WHERE code = 'FREE';

INSERT INTO rate_limit_policies (
    plan_id, path_pattern, http_method, capacity, refill_tokens, refill_period_seconds, enabled
)
SELECT id, '/**', 'ANY', 100, 600, 60, TRUE
FROM plans
WHERE code = 'PRO';

INSERT INTO rate_limit_policies (
    plan_id, path_pattern, http_method, capacity, refill_tokens, refill_period_seconds, enabled
)
SELECT id, '/**', 'ANY', 1000, 6000, 60, TRUE
FROM plans
WHERE code = 'ENTERPRISE';
