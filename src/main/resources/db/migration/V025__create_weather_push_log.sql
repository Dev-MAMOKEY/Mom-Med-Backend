-- Slice 07: 날씨 푸시 알림 중복 발송 방지 로그 테이블
-- 같은 (parent_id, rule_id, sent_date) 조합은 하루에 한 번만 발송합니다.

CREATE TABLE logs.weather_push_log (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   UUID      NOT NULL,
    rule_id     BIGINT    NOT NULL,
    sent_date   DATE      NOT NULL DEFAULT CURRENT_DATE,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_weather_push_log
    ON logs.weather_push_log (parent_id, rule_id, sent_date);

CREATE INDEX idx_weather_push_log_parent_date
    ON logs.weather_push_log (parent_id, sent_date);
