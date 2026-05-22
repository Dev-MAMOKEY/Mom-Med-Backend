-- Slice 00 smoke schema: 애플리케이션 사용자 기본 테이블
-- 목적:
--   1. Flyway가 실제 테이블/제약조건/인덱스/트리거를 정상 생성하는지 검증합니다.
--   2. 후속 슬라이스에서 보호자 계정 또는 인증 주체를 연결할 수 있는 최소 기반을 제공합니다.

CREATE TABLE IF NOT EXISTS app.users (
    -- 내부 식별자입니다. pgcrypto의 gen_random_uuid()를 사용해 애플리케이션 밖에서도 안전하게 생성됩니다.
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 로그인/알림/운영 식별에 사용할 이메일입니다. MVP에서는 unique smoke key 역할도 합니다.
    email VARCHAR(255) NOT NULL,

    -- 화면에 표시할 사용자 이름입니다. 아직 인증 정책이 없으므로 nullable로 둡니다.
    display_name VARCHAR(100),

    -- row 생성 시각입니다. 모든 app schema 테이블에서 공통으로 사용할 감사 컬럼 패턴입니다.
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- row 수정 시각입니다. 아래 trigger가 UPDATE마다 자동 갱신합니다.
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 같은 이메일로 사용자가 중복 생성되지 않도록 막습니다.
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 최신 가입/생성 순 조회를 빠르게 하기 위한 보조 인덱스입니다.
CREATE INDEX IF NOT EXISTS idx_users_created_at ON app.users (created_at DESC);

-- updated_at 자동 갱신 trigger입니다. V003의 공통 함수를 사용합니다.
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON app.users
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

-- psql이나 DB 도구에서 schema 의미를 바로 볼 수 있도록 DB comment를 남깁니다.
COMMENT ON TABLE app.users IS '애플리케이션 사용자 기본 테이블. Slice 00 Flyway smoke test와 후속 계정 연결 기반으로 사용한다.';
COMMENT ON COLUMN app.users.id IS '사용자 내부 UUID 기본키';
COMMENT ON COLUMN app.users.email IS '사용자 이메일. 중복 생성을 막기 위해 unique 제약조건을 둔다.';
COMMENT ON COLUMN app.users.display_name IS '사용자 표시 이름';
COMMENT ON COLUMN app.users.created_at IS 'row 생성 시각';
COMMENT ON COLUMN app.users.updated_at IS 'row 수정 시각. trg_users_updated_at trigger가 자동 갱신한다.';
