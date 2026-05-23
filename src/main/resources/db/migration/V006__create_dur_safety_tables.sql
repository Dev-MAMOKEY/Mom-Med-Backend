-- Slice 02 DUR safety tables.
-- HIRA DUR CSV is loaded into ref schema so downstream safety checks can query
-- normalized ingredient names with exact equality predicates. Do not use
-- substring or LIKE matching for these large reference tables.

CREATE TABLE ref.dur_combo_contraindications (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,

    -- ingredient_norm_a/b are the indexed matching keys used by DURRuleEngine.
    -- ingredient_name_a/b keep the original CSV values for audit and evidence text.
    ingredient_name_a   VARCHAR(500) NOT NULL,
    ingredient_norm_a   VARCHAR(200) NOT NULL,
    ingredient_code_a   VARCHAR(50),
    product_code_a      VARCHAR(50),
    product_name_a      VARCHAR(500),
    company_name_a      VARCHAR(200),
    reimbursement_a     VARCHAR(50),

    ingredient_name_b   VARCHAR(500) NOT NULL,
    ingredient_norm_b   VARCHAR(200) NOT NULL,
    ingredient_code_b   VARCHAR(50),
    product_code_b      VARCHAR(50),
    product_name_b      VARCHAR(500),
    company_name_b      VARCHAR(200),
    reimbursement_b     VARCHAR(50),

    gazette_no          VARCHAR(100),
    gazette_date        DATE,
    detail              TEXT,
    note                TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dur_combo_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_combo_contraindications IS
    'HIRA DUR 병용금기 CSV 적재 테이블. Slice 02는 ingredient_norm_a/b 정확 매칭으로 약쌍을 검사한다.';
COMMENT ON COLUMN ref.dur_combo_contraindications.source_row_hash IS
    'CSV 원본 행의 SHA-256 fingerprint. 같은 CSV를 다시 적재해도 중복 insert를 막는다.';
COMMENT ON COLUMN ref.dur_combo_contraindications.ingredient_norm_a IS
    'DrugNameNormalizer로 정규화한 왼쪽 성분명. LIKE 금지, equality lookup 전용 컬럼.';
COMMENT ON COLUMN ref.dur_combo_contraindications.ingredient_norm_b IS
    'DrugNameNormalizer로 정규화한 오른쪽 성분명. LIKE 금지, equality lookup 전용 컬럼.';

CREATE INDEX idx_dur_combo_norm_a ON ref.dur_combo_contraindications (ingredient_norm_a);
CREATE INDEX idx_dur_combo_norm_b ON ref.dur_combo_contraindications (ingredient_norm_b);
CREATE INDEX idx_dur_combo_pair ON ref.dur_combo_contraindications (ingredient_norm_a, ingredient_norm_b);

CREATE TRIGGER trg_dur_combo_contraindications_updated_at
    BEFORE UPDATE ON ref.dur_combo_contraindications
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE ref.dur_elderly_caution (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,

    -- ingredient_norm is the exact lookup key used only when patient age is 65+.
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    company_name        VARCHAR(200),
    gazette_date        DATE,
    gazette_no          VARCHAR(100),
    detail              TEXT,
    note                TEXT,
    reimbursement       VARCHAR(50),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dur_elderly_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_elderly_caution IS
    'HIRA DUR 노인주의 CSV 적재 테이블. 65세 이상 부모의 새 약 성분을 ingredient_norm으로 정확 조회한다.';
COMMENT ON COLUMN ref.dur_elderly_caution.ingredient_norm IS
    'DrugNameNormalizer로 정규화한 성분명. 노인주의 판정의 equality lookup 키.';

CREATE INDEX idx_dur_elderly_ingredient_norm ON ref.dur_elderly_caution (ingredient_norm);

CREATE TRIGGER trg_dur_elderly_caution_updated_at
    BEFORE UPDATE ON ref.dur_elderly_caution
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE ref.dur_elderly_nsaid_caution (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,

    -- NSAID 노인주의는 별도 CSV로 제공되지만 판정 흐름은 일반 노인주의와 동일하다.
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    company_name        VARCHAR(200),
    detail              TEXT,
    reimbursement       VARCHAR(50),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dur_elderly_nsaid_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_elderly_nsaid_caution IS
    'HIRA DUR NSAID 노인주의 CSV 적재 테이블. 65세 이상에서 일반 노인주의와 함께 조회한다.';
COMMENT ON COLUMN ref.dur_elderly_nsaid_caution.ingredient_norm IS
    'DrugNameNormalizer로 정규화한 성분명. NSAID 노인주의 판정의 equality lookup 키.';

CREATE INDEX idx_dur_elderly_nsaid_ingredient_norm ON ref.dur_elderly_nsaid_caution (ingredient_norm);

CREATE TRIGGER trg_dur_elderly_nsaid_caution_updated_at
    BEFORE UPDATE ON ref.dur_elderly_nsaid_caution
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE ref.dur_age_contraindication (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    age_limit           VARCHAR(100),
    gazette_no          VARCHAR(100),
    gazette_date        DATE,
    detail              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dur_age_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_age_contraindication IS
    'HIRA DUR 연령금기 테이블. Slice 02에서는 스키마만 만들고 Slice 05에서 적재/판정한다.';

CREATE INDEX idx_dur_age_ingredient_norm ON ref.dur_age_contraindication (ingredient_norm);

CREATE TRIGGER trg_dur_age_contraindication_updated_at
    BEFORE UPDATE ON ref.dur_age_contraindication
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE ref.dur_pregnancy_contraindication (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    pregnancy_grade     VARCHAR(100),
    gazette_no          VARCHAR(100),
    gazette_date        DATE,
    detail              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dur_pregnancy_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_pregnancy_contraindication IS
    'HIRA DUR 임부금기 테이블. Slice 02에서는 스키마만 만들고 Slice 05에서 적재/판정한다.';

CREATE INDEX idx_dur_pregnancy_ingredient_norm ON ref.dur_pregnancy_contraindication (ingredient_norm);

CREATE TRIGGER trg_dur_pregnancy_contraindication_updated_at
    BEFORE UPDATE ON ref.dur_pregnancy_contraindication
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
