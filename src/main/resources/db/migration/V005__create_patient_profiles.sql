-- app.patient_profiles: 부모(환자) 프로파일 테이블
-- 자녀가 어머니를 등록하는 중심 테이블. 약장·알레르기·기저질환이 모두 이 parent_id를 참조합니다.
CREATE TABLE app.patient_profiles (
    parent_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name        VARCHAR(50) NOT NULL,              -- "어머니", "아버지" 등 자녀가 붙인 이름
    birthdate           DATE NOT NULL,                     -- 만 나이 계산에 사용
    sex                 CHAR(1) NOT NULL CHECK (sex IN ('M', 'F')),
    address_sido        VARCHAR(50),                       -- 기상청 격자 좌표 변환에 사용 (Slice 06)
    address_sigungu     VARCHAR(50),
    address_dong        VARCHAR(50),
    nx                  SMALLINT,                          -- 기상청 격자 X (Slice 06에서 채움)
    ny                  SMALLINT,                          -- 기상청 격자 Y (Slice 06에서 채움)
    is_pregnant         BOOLEAN NOT NULL DEFAULT FALSE,
    consent_data_share  BOOLEAN NOT NULL DEFAULT FALSE,    -- 데이터 공유 동의
    consent_at          TIMESTAMPTZ,                       -- 동의 시각
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 주소 기반 지역별 조회 (날씨 룰셋 매핑에 사용)
CREATE INDEX idx_patient_address ON app.patient_profiles(address_sido, address_sigungu);

-- updated_at 자동 갱신 트리거
CREATE TRIGGER trg_patient_profiles_updated_at
    BEFORE UPDATE ON app.patient_profiles
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- 만 나이 계산 헬퍼 (앱/뷰에서 birthdate → 만 나이 변환)
CREATE OR REPLACE FUNCTION app.calc_age(birthdate DATE) RETURNS INT AS $$
    SELECT EXTRACT(YEAR FROM AGE(birthdate))::INT;
$$ LANGUAGE SQL IMMUTABLE;
