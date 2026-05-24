-- app.device_tokens: 자녀 디바이스 토큰 (Slice 04 v2 신규 — Slice 07 푸시용)
-- FCM/APNs/web push 토큰을 저장. token은 pgcrypto로 암호화 후 저장.
CREATE TABLE app.device_tokens (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    child_user_id   UUID,                                  -- 향후 자녀 계정 PRD에서 활성화
    platform        VARCHAR(10) NOT NULL CHECK (platform IN ('fcm', 'apns', 'web')),

    -- pgp_sym_encrypt(token_plain, app_encryption_key) 결과를 저장
    -- Java 레이어에서 절대 plain token을 이 컬럼에 직접 쓰지 않습니다.
    token_encrypted BYTEA NOT NULL,

    last_used_at    TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,                           -- NULL = 활성, NOT NULL = 취소됨
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 부모별 활성 토큰 조회 (푸시 발송 시 사용)
CREATE INDEX idx_dtok_parent_active ON app.device_tokens(parent_id) WHERE revoked_at IS NULL;
