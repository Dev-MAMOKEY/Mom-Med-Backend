-- Slice 08: 응급카드 — 병원 · 약국 · 응급카드 테이블 생성

-- 부모 단골 병원
CREATE TABLE app.parent_hospitals (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    ykiho           VARCHAR(500) NOT NULL,
    yadm_nm         VARCHAR(200) NOT NULL,
    cl_cd_nm        VARCHAR(50),
    addr            VARCHAR(500),
    telno           VARCHAR(50),
    x_pos           NUMERIC(12,6),
    y_pos           NUMERIC(12,6),
    is_regular      BOOLEAN NOT NULL DEFAULT FALSE,
    last_visited    DATE,
    added_by        VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_parent_hosp ON app.parent_hospitals(parent_id);
CREATE UNIQUE INDEX uniq_parent_ykiho ON app.parent_hospitals(parent_id, ykiho);

-- 병원 응급실 상세 정보 (ykiho 공유 캐시)
CREATE TABLE app.hospital_emergency_info (
    ykiho               VARCHAR(500) PRIMARY KEY,
    yadm_nm             VARCHAR(200),
    night_er_available  CHAR(1),
    night_er_phone_1    VARCHAR(50),
    night_er_phone_2    VARCHAR(50),
    day_er_available    CHAR(1),
    day_er_phone_1      VARCHAR(50),
    day_er_phone_2      VARCHAR(50),
    weekly_hours        JSONB,
    sun_closed          VARCHAR(100),
    holi_closed         VARCHAR(100),
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 부모 단골 약국
CREATE TABLE app.parent_pharmacies (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    ykiho           VARCHAR(500) NOT NULL,
    yadm_nm         VARCHAR(200) NOT NULL,
    addr            VARCHAR(500),
    telno           VARCHAR(50),
    x_pos           NUMERIC(12,6),
    y_pos           NUMERIC(12,6),
    is_regular      BOOLEAN NOT NULL DEFAULT FALSE,
    visit_count     INT NOT NULL DEFAULT 0,
    last_visited    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(parent_id, ykiho)
);
CREATE INDEX idx_parent_pharm ON app.parent_pharmacies(parent_id);

-- 응급카드 (QR 토큰 + snapshot)
CREATE TABLE app.emergency_cards (
    parent_id           UUID PRIMARY KEY REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    qr_token            VARCHAR(64) NOT NULL UNIQUE,
    snapshot            JSONB NOT NULL,
    snapshot_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until         TIMESTAMPTZ NOT NULL,
    access_count        INT NOT NULL DEFAULT 0,
    last_accessed_at    TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ
);
CREATE INDEX idx_em_card_token ON app.emergency_cards(qr_token) WHERE revoked_at IS NULL;
