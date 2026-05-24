-- Slice 03 NB interaction rows.
-- One verified extraction expands into many interaction rows so SafetyJudge can match partners quickly.

CREATE TABLE derived.nb_interactions (
    id                      BIGSERIAL PRIMARY KEY,
    extraction_id           BIGINT NOT NULL REFERENCES derived.nb_extractions(id) ON DELETE CASCADE,
    item_seq                VARCHAR(20) NOT NULL,
    drug_name               VARCHAR(200) NOT NULL,

    -- Slice 03 fills drug_drug rows. Slice 05 can later fill patient_class rows without ALTER.
    entry_type              VARCHAR(20) NOT NULL CHECK (entry_type IN ('drug_drug', 'patient_class')),

    partner_drug_ko         VARCHAR(300),
    partner_drug_norm       VARCHAR(200),
    partner_drug_en         VARCHAR(200),
    is_drug_group           BOOLEAN NOT NULL DEFAULT FALSE,

    patient_class_text      VARCHAR(300),
    patient_class_kcd       VARCHAR(50),

    risk_level              VARCHAR(30) NOT NULL CHECK (
        risk_level IN ('동시투여피해야함', '권장하지않음', '주의', '정보만')
    ),
    reason_summary          TEXT,
    source_quote            TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_nb_interaction_type_payload CHECK (
        (entry_type = 'drug_drug' AND partner_drug_ko IS NOT NULL)
        OR
        (entry_type = 'patient_class' AND patient_class_text IS NOT NULL)
    )
);

COMMENT ON TABLE derived.nb_interactions IS
    '검증된 NB 추출 결과를 SafetyJudge가 빠르게 조회할 수 있게 펼친 interaction 테이블.';
COMMENT ON COLUMN derived.nb_interactions.entry_type IS
    'drug_drug 또는 patient_class. Slice 03은 drug_drug만 채우고 Slice 05가 patient_class를 확장한다.';
COMMENT ON COLUMN derived.nb_interactions.partner_drug_norm IS
    'DrugNameNormalizer로 정규화한 상대 약물/식품/약물군 명칭. 단일 약물 exact match 또는 그룹 사전 매칭 키.';
COMMENT ON COLUMN derived.nb_interactions.is_drug_group IS
    'partner_drug_ko가 CYP3A4 저해제 같은 약물군이면 true. GROUP_TO_ATC_PREFIX로 확장 매칭한다.';
COMMENT ON COLUMN derived.nb_interactions.source_quote IS
    'LLM이 근거로 제시한 NB_DOC_DATA 원문 발췌. HallucinationVerifier를 통과한 quote만 사용자 노출한다.';

CREATE INDEX idx_nbi_drug_partner
    ON derived.nb_interactions(item_seq, partner_drug_norm)
    WHERE entry_type = 'drug_drug';

CREATE INDEX idx_nbi_patient_class
    ON derived.nb_interactions(item_seq, patient_class_kcd)
    WHERE entry_type = 'patient_class';

CREATE INDEX idx_nbi_partner_norm
    ON derived.nb_interactions(partner_drug_norm)
    WHERE entry_type = 'drug_drug';
