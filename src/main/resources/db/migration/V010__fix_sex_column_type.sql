-- CHAR(1)은 PostgreSQL에서 bpchar로 저장되므로 Hibernate VARCHAR 검증 실패 → VARCHAR(1)로 교체
ALTER TABLE app.patient_profiles
    ALTER COLUMN sex TYPE VARCHAR(1);
