-- V026: Slice 07 v2 — 날씨 관측 캐시 + Advisory 푸시 이력 파티션 테이블
-- (V025의 weather_push_log는 v2에서 advisory_push_log로 대체; 레거시 테이블은 유지)

-- 1) 격자별 날씨 관측 캐시 (관측일 기준 월별 파티션)
CREATE TABLE logs.weather_observations_daily (
    id             BIGSERIAL,
    observed_date  DATE        NOT NULL,
    nx             SMALLINT    NOT NULL,
    ny             SMALLINT    NOT NULL,
    tmx            NUMERIC(4,1),                          -- 오늘 일 최고기온 (폭염 판정)
    tmn            NUMERIC(4,1),                          -- 내일 아침 최저기온 (한파 판정)
    derived_alerts JSONB       NOT NULL DEFAULT '[]'::jsonb,
    base_date      VARCHAR(8)  NOT NULL,
    base_time      VARCHAR(4)  NOT NULL,
    raw_response   JSONB,
    fetched_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (observed_date, id),
    UNIQUE (observed_date, nx, ny)
) PARTITION BY RANGE (observed_date);

CREATE TABLE logs.weather_observations_daily_2026_05
    PARTITION OF logs.weather_observations_daily
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE logs.weather_observations_daily_2026_06
    PARTITION OF logs.weather_observations_daily
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE logs.weather_observations_daily_2026_07
    PARTITION OF logs.weather_observations_daily
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE INDEX idx_weather_obs_grid ON logs.weather_observations_daily (nx, ny, observed_date);

-- 2) Advisory 푸시 이력 (sent_at 기준 월별 파티션)
CREATE TABLE logs.advisory_push_log (
    id              BIGSERIAL,
    parent_id       UUID        NOT NULL,
    rule_id         BIGINT      NOT NULL,
    weather_obs_id  BIGINT,
    push_payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    delivery_status VARCHAR(20) NOT NULL
        CHECK (delivery_status IN ('sent','failed','simulated','skipped_fatigue','skipped_review')),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sent_at, id)
) PARTITION BY RANGE (sent_at);

CREATE TABLE logs.advisory_push_log_2026_05
    PARTITION OF logs.advisory_push_log
    FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');

CREATE TABLE logs.advisory_push_log_2026_06
    PARTITION OF logs.advisory_push_log
    FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

CREATE TABLE logs.advisory_push_log_2026_07
    PARTITION OF logs.advisory_push_log
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');

CREATE INDEX idx_push_log_parent_date ON logs.advisory_push_log (parent_id, sent_at DESC);
