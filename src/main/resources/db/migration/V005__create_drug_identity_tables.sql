-- Slice 01: 약 식별과 알약 외형 정보를 저장할 ref schema 테이블입니다.
-- ref schema는 외부 공공데이터에서 가져온 기준 데이터를 보관하는 영역입니다.

CREATE TABLE IF NOT EXISTS ref.drugs_master (
    -- 식약처 의약품 제품 허가정보의 기준 식별자입니다. 후속 DUR/NB/약장 기능의 join key로 사용합니다.
    item_seq            VARCHAR(20) PRIMARY KEY,

    -- 식약처 원문 약품명과 업체 정보입니다. 사용자 검색 결과와 후보 응답에 그대로 노출합니다.
    item_name           VARCHAR(300) NOT NULL,
    item_name_eng       VARCHAR(300),
    entp_name           VARCHAR(200) NOT NULL,
    entp_no             VARCHAR(20),

    -- 허가일과 의약품 구분입니다. 화면 표시와 추후 필터링에 사용합니다.
    item_permit_date    DATE,
    specialty_type      VARCHAR(20),

    -- EDI/ATC는 DUR, NB, 약물군 매칭에서 보조 key로 사용합니다.
    edi_code            VARCHAR(20),
    atc_code            VARCHAR(10),

    -- main_ingr_norm은 Slice 02 DUR 정확 매칭의 핵심 join key입니다.
    main_ingr_en        VARCHAR(500),
    main_ingr_norm      VARCHAR(200),

    -- chart_text는 외형 설명, nb_doc_data는 Slice 03 LLM 입력으로 저장만 수행합니다.
    chart_text          TEXT,
    nb_doc_data         TEXT,
    ee_doc_data         TEXT,
    ud_doc_data         TEXT,

    -- 식약처 CHANGE_DATE입니다. 값이 없으면 refreshed_at 기준 30일 TTL fallback을 사용합니다.
    source_change_date  DATE,
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drugs_main_ingr_norm ON ref.drugs_master(main_ingr_norm);
CREATE INDEX idx_drugs_atc_code ON ref.drugs_master(atc_code);
CREATE INDEX idx_drugs_edi_code ON ref.drugs_master(edi_code) WHERE edi_code IS NOT NULL;
CREATE INDEX idx_drugs_item_name_trgm ON ref.drugs_master USING gin (item_name gin_trgm_ops);

CREATE TRIGGER trg_drugs_master_updated_at
    BEFORE UPDATE ON ref.drugs_master
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE IF NOT EXISTS ref.pill_visuals (
    -- 약 마스터와 1:1로 연결되는 알약 외형 정보입니다.
    item_seq            VARCHAR(20) PRIMARY KEY REFERENCES ref.drugs_master(item_seq) ON DELETE CASCADE,

    -- 사용자가 실물 약과 대조할 때 가장 중요한 이미지와 외형 정보입니다.
    image_url           TEXT NOT NULL,
    drug_shape          VARCHAR(50),
    color_primary       VARCHAR(50),
    color_secondary     VARCHAR(50),
    print_front         VARCHAR(100),
    print_back          VARCHAR(100),
    line_front          VARCHAR(50),
    line_back           VARCHAR(50),
    length_long_mm      NUMERIC(5,2),
    length_short_mm     NUMERIC(5,2),
    thickness_mm        NUMERIC(5,2),
    form_name           VARCHAR(50),
    chart_text          TEXT,

    -- 낱알식별 API의 CHANGE_DATE입니다. 약 마스터와 별도로 갱신 여부를 판단할 수 있게 분리합니다.
    source_change_date  DATE,
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pill_form_name ON ref.pill_visuals(form_name);

CREATE TRIGGER trg_pill_visuals_updated_at
    BEFORE UPDATE ON ref.pill_visuals
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();
