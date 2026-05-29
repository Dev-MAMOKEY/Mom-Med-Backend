-- =============================================================================
-- 데모 시드 데이터 — Flyway가 docker 프로파일에서만 적재합니다.
-- (application-docker.yml의 spring.flyway.locations에 db/migration-demo 추가)
--
-- 운영(default profile)에서는 db/migration만 로드 → 이 파일이 적재되지 않으므로
-- 실 사용자 DB에 절대 들어가지 않습니다.
--
-- BLOCK 시연 시나리오에 필요한 최소 데이터:
--   1) 약 마스터 4종 — 메트포르민·이오헥솔·암로디핀·아세트아미노펜
--   2) 부모 2명 — 어머니·아버지 (frontend mock UUID와 정확 일치)
--   3) 어머니의 약장에 메트포르민 1개 — 이오헥솔 추가 시 BLOCK 발생 출발점
-- =============================================================================

-- 약 마스터 4종 (main_ingr_norm은 DUR 정규화 키와 일치해야 BLOCK 판정 동작)
INSERT INTO ref.drugs_master (
  item_seq, item_name, entp_name, atc_code,
  main_ingr_en, main_ingr_norm, specialty_type
) VALUES
  ('DEMO_MET_001', '메트포르민염산염정 500mg', '대웅제약',         'A10BA02', 'METFORMIN HYDROCHLORIDE',  'metformin',    'ETC'),
  ('DEMO_IOH_001', '이오헥솔 300mg/mL 주사액', '한국GE헬스케어',   'V08AB02', 'IOHEXOL',                  'iohexol',      'ETC'),
  ('DEMO_AML_001', '암로디핀베실산염정 5mg',   '한국화이자제약',   'C08CA01', 'AMLODIPINE BESYLATE',      'amlodipine',   'ETC'),
  ('DEMO_ACE_001', '아세트아미노펜정 500mg',   '한국얀센',         'N02BE01', 'ACETAMINOPHEN',            'acetaminophen','OTC')
ON CONFLICT (item_seq) DO NOTHING;

-- 부모 2명 — UUID는 frontend mocks/parents.ts의 어머니/아버지와 정확히 일치해야 함
INSERT INTO app.patient_profiles (parent_id, display_name, birthdate, sex) VALUES
  ('530a7d32-7451-4c20-a32b-599b05eefa5a', '어머니', '1954-03-15', 'F'),
  ('50a5fcc1-8ae1-48d5-a5a9-601d321d186e', '아버지', '1951-08-22', 'M')
ON CONFLICT (parent_id) DO NOTHING;

-- 어머니의 약장에 메트포르민 1개 — 이오헥솔 추가 시 DUR 병용금기로 BLOCK 발생
-- partial unique index (deleted_at IS NULL) 때문에 ON CONFLICT 대신 NOT EXISTS 사용
INSERT INTO app.patient_medications (parent_id, item_seq, drug_name, ingredient_norm)
SELECT '530a7d32-7451-4c20-a32b-599b05eefa5a', 'DEMO_MET_001', '메트포르민염산염정 500mg', 'metformin'
WHERE NOT EXISTS (
  SELECT 1 FROM app.patient_medications
  WHERE parent_id = '530a7d32-7451-4c20-a32b-599b05eefa5a'
    AND item_seq = 'DEMO_MET_001'
    AND deleted_at IS NULL
);
