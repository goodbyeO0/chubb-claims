-- PostgreSQL 17. UUIDs are assigned by the application. Store all instants as timestamptz.
CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    username text NOT NULL UNIQUE CHECK (username = lower(username) AND btrim(username) <> ''),
    display_name text NOT NULL CHECK (btrim(display_name) <> ''),
    role text NOT NULL CHECK (role IN ('CLAIMANT', 'OFFICER')),
    password_hash text NOT NULL CHECK (btrim(password_hash) <> '')
);

CREATE SEQUENCE claim_ref_seq;

CREATE TABLE claim (
    id uuid PRIMARY KEY,
    claim_ref text NOT NULL UNIQUE DEFAULT ('CL-' || nextval('claim_ref_seq')::text),
    claimant_id uuid NOT NULL REFERENCES app_user(id),
    officer_id uuid REFERENCES app_user(id),
    kind text NOT NULL CHECK (kind IN ('MOTOR','PROPERTY')),
    market varchar(2) NOT NULL,
    currency varchar(3) NOT NULL,
    incident_on date NOT NULL,
    description text NOT NULL CHECK (btrim(description) <> '' AND length(description) <= 2000),
    status text NOT NULL CHECK (status IN ('SUBMITTED','UNDER_REVIEW','AWAITING_INFO','SETTLED','REJECTED')),
    reserve numeric(18,2) CHECK (reserve >= 0),
    settlement numeric(18,2) CHECK (settlement > 0),
    version bigint NOT NULL CHECK (version >= 1),
    submitted_at timestamptz NOT NULL,
    closed_at timestamptz,
    CHECK ((market, currency) IN (('MY','MYR'),('SG','SGD'),('TH','THB'),('ID','IDR'),('PH','PHP'),('HK','HKD'))),
    CHECK ((status = 'SUBMITTED') = (officer_id IS NULL)),
    CHECK ((status IN ('SETTLED','REJECTED')) = (closed_at IS NOT NULL)),
    CHECK ((status = 'SETTLED') = (settlement IS NOT NULL)),
    CHECK (status <> 'SETTLED' OR (reserve IS NOT NULL AND reserve = 0)),
    CHECK (status <> 'REJECTED' OR reserve IS NULL OR reserve = 0),
    CHECK (closed_at IS NULL OR closed_at >= submitted_at)
);

CREATE TABLE information_request (
    id uuid PRIMARY KEY,
    claim_id uuid NOT NULL REFERENCES claim(id),
    question text NOT NULL CHECK (btrim(question) <> '' AND length(question) <= 2000),
    asked_at timestamptz NOT NULL,
    answer text CHECK (btrim(answer) <> '' AND length(answer) <= 2000),
    answered_at timestamptz,
    CHECK ((answer IS NULL) = (answered_at IS NULL)),
    CHECK (answered_at IS NULL OR answered_at >= asked_at)
);
CREATE UNIQUE INDEX one_open_request ON information_request(claim_id) WHERE answered_at IS NULL;

CREATE TABLE claim_event (
    id uuid PRIMARY KEY,
    claim_id uuid NOT NULL REFERENCES claim(id),
    claim_version bigint NOT NULL CHECK (claim_version >= 1),
    actor_id uuid NOT NULL REFERENCES app_user(id),
    event_type text NOT NULL CHECK (event_type IN ('CLAIM_SUBMITTED','CLAIM_ASSIGNED','INFO_REQUESTED','INFO_PROVIDED','RESERVE_SET','CLAIM_SETTLED','CLAIM_REJECTED')),
    visibility text NOT NULL CHECK (visibility IN ('CLAIMANT_VISIBLE','STAFF_ONLY')),
    details jsonb NOT NULL CHECK (jsonb_typeof(details) = 'object'),
    occurred_at timestamptz NOT NULL,
    UNIQUE (claim_id, claim_version),
    CHECK ((event_type = 'RESERVE_SET') = (visibility = 'STAFF_ONLY'))
);

CREATE TABLE outbox (
    event_id uuid PRIMARY KEY REFERENCES claim_event(id),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    published_at timestamptz
);
CREATE INDEX outbox_pending ON outbox(event_id) WHERE published_at IS NULL;

CREATE TABLE exposure_snapshot (
    claim_id uuid PRIMARY KEY REFERENCES claim(id),
    claim_version bigint NOT NULL CHECK (claim_version >= 1),
    currency varchar(3) NOT NULL,
    is_open boolean NOT NULL,
    reserve numeric(18,2) CHECK (reserve >= 0),
    applied_at timestamptz NOT NULL
);

CREATE INDEX claim_queue ON claim(status, submitted_at, id);
CREATE INDEX claim_owner ON claim(claimant_id, submitted_at, id);
CREATE INDEX claim_officer ON claim(officer_id, status);
