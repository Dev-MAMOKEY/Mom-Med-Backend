-- Slice 00: updated_at 자동 갱신용 공통 trigger 함수
-- 각 슬라이스에서 테이블을 만들 때 BEFORE UPDATE trigger에 연결해 사용합니다.

-- public에 두는 이유:
-- 여러 schema(ref/app/derived/logs)의 테이블이 같은 함수를 공유할 수 있게 하기 위함입니다.
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    -- UPDATE가 발생하면 애플리케이션 코드가 직접 값을 넣지 않아도 updated_at을 현재 시각으로 갱신합니다.
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 사용 예시는 아래와 같습니다. 실제 trigger 부착은 각 테이블을 만드는 후속 슬라이스에서 수행합니다.
-- CREATE TRIGGER trg_drugs_master_updated_at
--     BEFORE UPDATE ON ref.drugs_master
--     FOR EACH ROW
--     EXECUTE FUNCTION public.update_updated_at_column();
