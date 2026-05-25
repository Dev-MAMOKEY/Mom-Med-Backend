-- DUR CSV의 일부 임부금기·연령금기 행에서 ingredient_code / product_code 셀에
-- 50자를 초과하는 한글 설명이 들어 있어 DataIntegrityViolationException 발생.
-- 모든 DUR ref 테이블의 코드 컬럼을 VARCHAR(200)으로 일괄 확장.

ALTER TABLE ref.dur_pregnancy_contraindication
    ALTER COLUMN ingredient_code TYPE VARCHAR(200),
    ALTER COLUMN product_code     TYPE VARCHAR(200);

ALTER TABLE ref.dur_age_contraindication
    ALTER COLUMN ingredient_code TYPE VARCHAR(200),
    ALTER COLUMN product_code     TYPE VARCHAR(200);

ALTER TABLE ref.dur_elderly_caution
    ALTER COLUMN ingredient_code TYPE VARCHAR(200),
    ALTER COLUMN product_code     TYPE VARCHAR(200);

ALTER TABLE ref.dur_elderly_nsaid_caution
    ALTER COLUMN ingredient_code TYPE VARCHAR(200),
    ALTER COLUMN product_code     TYPE VARCHAR(200);

ALTER TABLE ref.dur_combo_contraindications
    ALTER COLUMN ingredient_code_a TYPE VARCHAR(200),
    ALTER COLUMN product_code_a    TYPE VARCHAR(200),
    ALTER COLUMN ingredient_code_b TYPE VARCHAR(200),
    ALTER COLUMN product_code_b    TYPE VARCHAR(200);
