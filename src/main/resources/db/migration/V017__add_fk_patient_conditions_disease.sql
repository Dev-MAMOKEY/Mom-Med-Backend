-- Slice 05: patient_conditions.kcd_code → ref.disease_master.sick_cd FK 추가
-- kcd_code는 NULL 허용(질환명만으로 등록 가능)이므로 nullable FK로 설정.
-- kcd_code가 있을 때만 ref.disease_master에 존재해야 함.

ALTER TABLE app.patient_conditions
    ADD CONSTRAINT fk_patient_conditions_kcd_code
    FOREIGN KEY (kcd_code) REFERENCES ref.disease_master(sick_cd);
