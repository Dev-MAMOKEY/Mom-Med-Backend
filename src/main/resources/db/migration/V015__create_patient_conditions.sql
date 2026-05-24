-- Slice 05: 부모 기저질환 테이블
-- deleted_at IS NULL = 활성 기저질환 (soft delete 패턴)
-- condition_norm은 DrugNameNormalizer 또는 HIRA 코드 정규화 후 저장

CREATE TABLE app.patient_conditions (
    id             BIGSERIAL    PRIMARY KEY,
    parent_id      UUID         NOT NULL,
    condition_name VARCHAR(200) NOT NULL,
    condition_norm VARCHAR(200),
    kcd_code       VARCHAR(20),
    severity       VARCHAR(20),
    diagnosed_at   DATE,
    notes          TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ
);

-- 동일 질환 중복 등록 방지 (활성 상태에서만)
CREATE UNIQUE INDEX uq_patient_conditions_active
    ON app.patient_conditions(parent_id, condition_name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_patient_conditions_parent
    ON app.patient_conditions(parent_id, created_at DESC);
