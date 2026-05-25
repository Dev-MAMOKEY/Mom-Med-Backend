-- hospital_emergency_info.night_er_available / day_er_available 컬럼을
-- CHAR(1)(bpchar) → VARCHAR(1)으로 변경.
-- Hibernate는 String 필드를 VARCHAR로 매핑하므로 ddl-auto: validate 통과를 위해 필요합니다.

ALTER TABLE app.hospital_emergency_info
    ALTER COLUMN night_er_available TYPE VARCHAR(1),
    ALTER COLUMN day_er_available   TYPE VARCHAR(1);
