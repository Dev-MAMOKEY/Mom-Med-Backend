-- Slice 00: PostgreSQL 확장 기능 준비
-- 후속 슬라이스에서 검색, 암호화, 인덱싱, 쿼리 관측에 사용할 공통 확장입니다.
-- IF NOT EXISTS를 사용해 로컬/CI/운영에서 여러 번 실행되어도 안전하게 둡니다.

-- 한글/부분 문자열 검색 및 유사도 검색에 사용할 trigram 확장입니다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- UUID 생성과 pgp_sym_encrypt/pgp_sym_decrypt 기반 컬럼 암호화에 사용할 확장입니다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 여러 타입을 함께 다루는 GIN 인덱스가 필요할 때 사용할 확장입니다.
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- 느린 쿼리와 자주 호출되는 쿼리를 DB 레벨에서 관측하기 위한 확장입니다.
-- 로컬 Docker PostgreSQL에서는 바로 생성되지만, 관리형 DB에서는 별도 권한/설정이 필요할 수 있습니다.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
