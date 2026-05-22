-- logs.safety_check_log: 약 추가 시 안전 검사 결과 기록 (월별 RANGE 파티션)
-- BLOCK 포함 모든 판정을 기록. checked_at 기준으로 월별 파티션 분리.
CREATE TABLE logs.safety_check_log (
    id                  BIGSERIAL,
    parent_id           UUID NOT NULL,
    new_drug_item_seq   VARCHAR(20) NOT NULL,
    decision            VARCHAR(10) NOT NULL CHECK (decision IN ('BLOCK', 'WARN', 'INFO', 'ALLOW')),
    evidence_summary    JSONB NOT NULL,
    checked_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (checked_at, id)
) PARTITION BY RANGE (checked_at);

-- 2026년 5~7월 파티션 (경진대회 기간 커버)
CREATE TABLE logs.safety_check_log_2026_05
    PARTITION OF logs.safety_check_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE logs.safety_check_log_2026_06
    PARTITION OF logs.safety_check_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE logs.safety_check_log_2026_07
    PARTITION OF logs.safety_check_log
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- 부모별 검사 이력 조회 (최신순)
CREATE INDEX idx_safety_log_parent ON logs.safety_check_log(parent_id, checked_at DESC);
