-- Slice 06: weather_rules
-- 부모 기저질환 코드와 기상특보명을 정확 매칭해서 Slice 07 푸시 후보 advisory로 변환하는 정적 룰셋입니다.
-- lookup key는 (disease_code, weather_alert)이며, rule_id는 Slice 07의 중복 푸시 방지 키로 사용됩니다.

CREATE TABLE ref.weather_rules (
    id                      BIGSERIAL PRIMARY KEY,
    rule_version            VARCHAR(20) NOT NULL,
    disease_code            VARCHAR(10) NOT NULL,
    disease_name            VARCHAR(100) NOT NULL,
    weather_alert           VARCHAR(50) NOT NULL,
    severity                VARCHAR(20) NOT NULL CHECK (severity IN ('관심', '주의', '경고', '위험')),
    title                   VARCHAR(100) NOT NULL,
    message_template        TEXT NOT NULL,
    specific_drugs_to_note  JSONB NOT NULL DEFAULT '[]'::jsonb,
    patient_actions         JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_citations        JSONB NOT NULL DEFAULT '[]'::jsonb,
    rationale               TEXT,
    general_knowledge_used  BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by             VARCHAR(100),
    approved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_weather_rules_version_disease_alert
        UNIQUE (rule_version, disease_code, weather_alert)
);

-- WeatherDiseaseAdvisor.lookupRules()가 disease_code + weather_alert 정확 매칭으로 사용합니다.
CREATE INDEX idx_weather_rules_lookup
    ON ref.weather_rules(disease_code, weather_alert);

-- 의료진 검수가 필요한 룰만 빠르게 조회하기 위한 부분 인덱스입니다.
CREATE INDEX idx_weather_rules_review
    ON ref.weather_rules(general_knowledge_used)
    WHERE general_knowledge_used = TRUE AND approved_at IS NULL;

CREATE TRIGGER trg_weather_rules_updated_at
    BEFORE UPDATE ON ref.weather_rules
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
