-- app.patient_medications: 부모 약장 테이블 (Slice 04)
-- 부모가 현재 복용 중인 약을 관리. soft delete 패턴으로 이력 보존.
CREATE TABLE app.patient_medications (
    id                  BIGSERIAL PRIMARY KEY,
    parent_id           UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,

    -- 식약처 품목기준코드. ref.drugs_master의 item_seq와 일치 (외래 키는 ETL 완료 후 추가 가능).
    item_seq            VARCHAR(20) NOT NULL,

    -- 약품명과 성분 정규화명은 DrugMaster에서 복사해 캐싱. 마스터 테이블이 없어도 표시 가능.
    drug_name           VARCHAR(300) NOT NULL,
    ingredient_norm     VARCHAR(200),

    started_on          DATE,
    memo                TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ             -- NULL = 복용중, NOT NULL = 삭제(이력 보존)
);

-- 같은 부모가 동일 약을 중복 추가 방지 (활성 레코드에 한해 unique)
CREATE UNIQUE INDEX uq_patient_medications_active
    ON app.patient_medications(parent_id, item_seq)
    WHERE deleted_at IS NULL;

-- 부모별 약장 조회 (최신순)
CREATE INDEX idx_patient_medications_parent
    ON app.patient_medications(parent_id, created_at DESC);
