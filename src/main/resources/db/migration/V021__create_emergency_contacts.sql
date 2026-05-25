-- Slice 08: 비상연락처 테이블
CREATE TABLE app.emergency_contacts (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    relationship    VARCHAR(50)  NOT NULL,
    phone           VARCHAR(50)  NOT NULL,
    priority        INT  NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_emergency_contacts_parent ON app.emergency_contacts(parent_id);
