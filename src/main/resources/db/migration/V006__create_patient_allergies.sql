-- app.patient_allergies: 부모의 알레르기 정보 테이블 (Slice 04 v2 신규)
-- deleted_at IS NULL = 현재 활성 알레르기. soft delete로 이력 보존.
CREATE TABLE app.patient_allergies (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    allergen_type   VARCHAR(20) NOT NULL CHECK (allergen_type IN ('drug', 'food', 'env', 'other')),
    allergen_name   VARCHAR(200) NOT NULL,                 -- 원본 텍스트 (예: "페니실린")
    allergen_norm   VARCHAR(200),                          -- 정규화된 약물명 (drug 타입일 때 normalize_drug_name 적용)
    severity        VARCHAR(20) CHECK (severity IN ('severe', 'moderate', 'mild', 'unknown')),
    notes           TEXT,
    confirmed_at    DATE,                                  -- 진단 확인일 (선택)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ                            -- NULL = 활성, NOT NULL = 삭제됨
);

-- 부모별 활성 알레르기 목록 조회 (주요 쿼리 경로)
CREATE INDEX idx_pall_parent_active ON app.patient_allergies(parent_id) WHERE deleted_at IS NULL;

-- 정규화된 약물명으로 알레르기 검색 (SafetyJudge가 사용)
CREATE INDEX idx_pall_allergen_norm ON app.patient_allergies(allergen_norm) WHERE allergen_norm IS NOT NULL;
