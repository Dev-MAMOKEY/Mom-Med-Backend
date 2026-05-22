-- Slice 00: PostgreSQL 확장 기능 준비
-- 후속 슬라이스에서 검색, 암호화, 인덱싱, 쿼리 관측에 사용할 공통 확장을 먼저 활성화합니다.
-- IF NOT EXISTS를 사용해 로컬/CI/운영에서 여러 번 실행되어도 안전하게 만듭니다.

-- 약 이름, 질병명처럼 부분 문자열 검색이나 유사도 검색이 필요한 컬럼에 사용할 trigram 확장입니다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- UUID 생성과 pgp_sym_encrypt/pgp_sym_decrypt 기반 컬럼 암호화에 사용할 확장입니다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 여러 타입을 섞은 복합 GIN 인덱스가 필요할 때 사용할 확장입니다.
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- 느린 쿼리와 자주 호출되는 쿼리를 DB 내부에서 관측하기 위한 확장입니다.
-- 관리형 DB에서는 별도 권한이 필요할 수 있으므로 배포 환경에서 권한을 확인해야 합니다.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
