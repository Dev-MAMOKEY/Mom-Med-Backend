-- Slice 03 NB extraction cache.
-- Each row represents one LLM extraction attempt for one drug label version.
-- The unique key prevents repeated Gemini calls for the same item/version/model/prompt.

CREATE TABLE derived.nb_extractions (
    id                      BIGSERIAL PRIMARY KEY,
    item_seq                VARCHAR(20) NOT NULL REFERENCES ref.drugs_master(item_seq) ON DELETE CASCADE,
    drug_change_date        DATE,
    extraction_json         JSONB NOT NULL,
    verified                BOOLEAN NOT NULL,
    verification_summary    JSONB,
    llm_model               VARCHAR(50) NOT NULL,
    prompt_version          VARCHAR(20) NOT NULL,
    token_input             INT,
    token_output            INT,
    cost_usd                NUMERIC(10,6),
    extracted_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- NULLS NOT DISTINCT keeps labels with unknown CHANGE_DATE cacheable as one logical version.
    CONSTRAINT uq_nb_extraction_cache_key UNIQUE NULLS NOT DISTINCT
        (item_seq, drug_change_date, llm_model, prompt_version)
);

COMMENT ON TABLE derived.nb_extractions IS
    '식약처 NB_DOC_DATA를 LLM으로 구조화 추출한 원본 응답 캐시. 검증 결과와 비용/토큰 메트릭을 함께 저장한다.';
COMMENT ON COLUMN derived.nb_extractions.item_seq IS
    'ref.drugs_master.item_seq. 약 라벨 원문과 SafetyJudge NB 매칭의 기준 약 식별자.';
COMMENT ON COLUMN derived.nb_extractions.drug_change_date IS
    '식약처 CHANGE_DATE. 같은 약이라도 라벨 변경 시 새 추출을 만들기 위한 캐시 키.';
COMMENT ON COLUMN derived.nb_extractions.extraction_json IS
    'Gemini가 반환한 strict JSON 전체. 재처리와 회귀 비교를 위해 원본 구조를 보존한다.';
COMMENT ON COLUMN derived.nb_extractions.verified IS
    'HallucinationVerifier가 source_quote를 원문에서 확인했는지 여부. 사용자 노출은 true만 허용한다.';
COMMENT ON COLUMN derived.nb_extractions.verification_summary IS
    '환각 검증 집계 JSON. total/exact/normalized/fuzzy/hallucinated 값을 담는다.';

CREATE INDEX idx_nb_ext_item_seq ON derived.nb_extractions(item_seq);
CREATE INDEX idx_nb_ext_verified ON derived.nb_extractions(verified) WHERE verified = true;
