-- Slice 05: HIRA 질병코드 마스터 테이블
-- HIRA 11984 CSV (cp949)를 DurCsvLoader.loadDiseaseMaster()로 적재.
-- ConditionService가 kcd_code 유효성을 HIRA API로 검증한 뒤 이 테이블을 FK 참조 기반으로 사용.

CREATE TABLE ref.disease_master (
    sick_cd             VARCHAR(10)  PRIMARY KEY,
    sick_nm             VARCHAR(200) NOT NULL,
    sick_eng_nm         VARCHAR(300),
    complete_code_flag  CHAR(1),
    main_diagnosis      CHAR(1),
    infectious_grade    VARCHAR(20),
    sex_restriction     CHAR(1),
    age_max             INT,
    age_min             INT,
    yang_han_type       VARCHAR(20),
    source_csv_date     VARCHAR(20)  NOT NULL DEFAULT '2024.11',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ref.disease_master IS
    'HIRA 질병코드(KCD) 마스터. Slice 05에서 patient_conditions.kcd_code FK 참조 기반으로 사용.';

-- pg_trgm 기반 한글명 유사 검색 (HIRA API 오프라인 보완용)
CREATE INDEX idx_disease_master_nm_trgm ON ref.disease_master USING gin (sick_nm gin_trgm_ops);
