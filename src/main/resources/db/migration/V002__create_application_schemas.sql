-- Slice 00: 4-schema 기본 구조 생성
-- ref/app/derived/logs를 분리해 외부 기준 데이터, 사용자 데이터, 가공 데이터, 로그성 데이터를 명확히 나눕니다.

-- 외부 API/CSV에서 가져온 기준 데이터가 들어갑니다. 예: 약 마스터, DUR, ATC, 질병 코드.
CREATE SCHEMA IF NOT EXISTS ref;

-- 사용자가 앱에서 직접 만들거나 수정하는 데이터가 들어갑니다. 예: 부모 프로필, 약장, 알림 토큰.
CREATE SCHEMA IF NOT EXISTS app;

-- 원천 데이터를 가공하거나 캐시한 데이터가 들어갑니다. 예: NB 추출 결과, 안전판정 중간 결과.
CREATE SCHEMA IF NOT EXISTS derived;

-- 시간이 지나며 쌓이는 감사/판정/외부 호출 로그가 들어갑니다.
CREATE SCHEMA IF NOT EXISTS logs;

-- 개발 편의를 위해 현재 접속한 DB의 기본 search_path를 지정합니다.
-- DB 이름을 mom_med로 고정하지 않고 current_database()를 사용해 로컬/CI DB 이름 차이를 흡수합니다.
DO $$
BEGIN
    EXECUTE format(
        'ALTER DATABASE %I SET search_path TO app, ref, derived, logs, public',
        current_database()
    );
END;
$$;
