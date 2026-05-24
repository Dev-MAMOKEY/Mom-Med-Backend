-- 복합 성분명(multi-ingredient)의 정규화 값이 200자를 초과하는 경우가 있어
-- ingredient_norm 관련 컬럼 전체를 VARCHAR(500)으로 확장.

ALTER TABLE ref.dur_combo_contraindications
    ALTER COLUMN ingredient_norm_a TYPE VARCHAR(500),
    ALTER COLUMN ingredient_norm_b TYPE VARCHAR(500);

ALTER TABLE ref.dur_elderly_caution
    ALTER COLUMN ingredient_norm TYPE VARCHAR(500);

ALTER TABLE ref.dur_elderly_nsaid_caution
    ALTER COLUMN ingredient_norm TYPE VARCHAR(500);

ALTER TABLE ref.dur_age_contraindication
    ALTER COLUMN ingredient_norm TYPE VARCHAR(500);

ALTER TABLE ref.dur_pregnancy_contraindication
    ALTER COLUMN ingredient_norm TYPE VARCHAR(500);
