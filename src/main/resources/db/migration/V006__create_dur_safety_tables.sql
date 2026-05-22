-- Slice 02 DUR safety tables.
-- HIRA DUR CSV는 외부 API 호출 없이 로컬 파일을 DB에 적재한 뒤 조회한다.
-- 병용금기 조회는 LIKE 검색을 금지하고, 정규화된 성분명 ingredient_norm_* 컬럼의 = 정확 매칭만 사용한다.

CREATE TABLE IF NOT EXISTS ref.dur_combo_contraindications (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,

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

    gazette_no          VARCHAR(50),
    gazette_date        DATE,
    detail              TEXT,
    note                TEXT,
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_dur_combo_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_combo_contraindications IS
    'HIRA DUR 병용금기 CSV 적재 테이블. ingredient_norm_a/b를 = 정확 매칭하여 약쌍 금기를 조회한다.';
COMMENT ON COLUMN ref.dur_combo_contraindications.ingredient_norm_a IS
    'DrugNameNormalizer로 정규화한 A 성분명. LIKE 없이 = 조건으로 조회하는 핵심 매칭 키.';
COMMENT ON COLUMN ref.dur_combo_contraindications.ingredient_norm_b IS
    'DrugNameNormalizer로 정규화한 B 성분명. 양방향 조회 시 ingredient_norm_a와 함께 사용한다.';
COMMENT ON COLUMN ref.dur_combo_contraindications.source_row_hash IS
    'CSV row의 안정적인 SHA-256 fingerprint. 같은 CSV를 다시 적재해도 중복 INSERT를 막는다.';

CREATE INDEX IF NOT EXISTS idx_dur_combo_norm_a
    ON ref.dur_combo_contraindications (ingredient_norm_a);
CREATE INDEX IF NOT EXISTS idx_dur_combo_norm_b
    ON ref.dur_combo_contraindications (ingredient_norm_b);
CREATE INDEX IF NOT EXISTS idx_dur_combo_pair
    ON ref.dur_combo_contraindications (ingredient_norm_a, ingredient_norm_b);

CREATE TRIGGER trg_dur_combo_updated_at BEFORE UPDATE ON ref.dur_combo_contraindications
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE IF NOT EXISTS ref.dur_elderly_caution (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    company_name        VARCHAR(200),
    gazette_date        DATE,
    gazette_no          VARCHAR(50),
    detail              TEXT,
    note                TEXT,
    reimbursement       VARCHAR(50),
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_dur_elderly_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_elderly_caution IS
    '65세 이상 부모 약장 판정에 사용하는 HIRA DUR 노인주의 CSV 적재 테이블.';
COMMENT ON COLUMN ref.dur_elderly_caution.ingredient_norm IS
    'DrugNameNormalizer로 정규화한 성분명. 65세 이상일 때 = 정확 매칭으로 WARN evidence를 조회한다.';

CREATE INDEX IF NOT EXISTS idx_dur_elderly_ingredient_norm
    ON ref.dur_elderly_caution (ingredient_norm);

CREATE TRIGGER trg_dur_elderly_updated_at BEFORE UPDATE ON ref.dur_elderly_caution
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE IF NOT EXISTS ref.dur_elderly_nsaid_caution (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    company_name        VARCHAR(200),
    detail              TEXT,
    reimbursement       VARCHAR(50),
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_dur_elderly_nsaid_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_elderly_nsaid_caution IS
    '65세 이상에서 NSAID 계열 약물 주의 evidence를 만들기 위한 HIRA DUR 노인주의 NSAID 테이블.';
COMMENT ON COLUMN ref.dur_elderly_nsaid_caution.ingredient_norm IS
    'DrugNameNormalizer로 정규화한 NSAID 성분명. = 정확 매칭으로 WARN evidence를 조회한다.';

CREATE INDEX IF NOT EXISTS idx_dur_elderly_nsaid_ingredient_norm
    ON ref.dur_elderly_nsaid_caution (ingredient_norm);

CREATE TRIGGER trg_dur_elderly_nsaid_updated_at BEFORE UPDATE ON ref.dur_elderly_nsaid_caution
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Slice 05에서 실제 데이터를 적재한다. Slice 02에서는 후속 작업이 같은 스키마에 맞춰 개발 가능하도록 빈 테이블만 만든다.
CREATE TABLE IF NOT EXISTS ref.dur_age_contraindication (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    company_name        VARCHAR(200),
    age_condition       VARCHAR(100),
    gazette_no          VARCHAR(50),
    gazette_date        DATE,
    detail              TEXT,
    note                TEXT,
    reimbursement       VARCHAR(50),
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_dur_age_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_age_contraindication IS
    'Slice 05에서 적재할 연령금기 테이블. Slice 02에서는 스키마만 준비한다.';

CREATE INDEX IF NOT EXISTS idx_dur_age_ingredient_norm
    ON ref.dur_age_contraindication (ingredient_norm);

CREATE TRIGGER trg_dur_age_updated_at BEFORE UPDATE ON ref.dur_age_contraindication
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE IF NOT EXISTS ref.dur_pregnancy_contraindication (
    id                  BIGSERIAL PRIMARY KEY,
    source_row_hash     VARCHAR(64) NOT NULL,
    ingredient_name     VARCHAR(500) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    ingredient_code     VARCHAR(50),
    product_code        VARCHAR(50),
    product_name        VARCHAR(500),
    company_name        VARCHAR(200),
    pregnancy_grade     VARCHAR(100),
    gazette_no          VARCHAR(50),
    gazette_date        DATE,
    detail              TEXT,
    note                TEXT,
    reimbursement       VARCHAR(50),
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_dur_pregnancy_source_row_hash UNIQUE (source_row_hash)
);

COMMENT ON TABLE ref.dur_pregnancy_contraindication IS
    'Slice 05에서 적재할 임부금기 테이블. Slice 02에서는 스키마만 준비한다.';

CREATE INDEX IF NOT EXISTS idx_dur_pregnancy_ingredient_norm
    ON ref.dur_pregnancy_contraindication (ingredient_norm);

CREATE TRIGGER trg_dur_pregnancy_updated_at BEFORE UPDATE ON ref.dur_pregnancy_contraindication
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
