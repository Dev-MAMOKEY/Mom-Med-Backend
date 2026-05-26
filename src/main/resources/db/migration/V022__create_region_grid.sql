-- Slice 06: region_grid
-- 기상청 단기예보 격자 좌표(nx, ny)를 행정구역 주소와 연결하는 기준 테이블입니다.
-- 부모 주소를 nx/ny로 변환한 뒤 Slice 07에서 기상청 예보 조회 키로 사용합니다.

CREATE TABLE ref.region_grid (
    id              BIGSERIAL PRIMARY KEY,
    admin_code      VARCHAR(20),
    sido            VARCHAR(50) NOT NULL,
    sigungu         VARCHAR(50),
    eup_myeon_dong  VARCHAR(50),
    nx              SMALLINT NOT NULL,
    ny              SMALLINT NOT NULL,
    longitude       NUMERIC(10, 6),
    latitude        NUMERIC(10, 6),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_region_grid_location
        UNIQUE NULLS NOT DISTINCT (sido, sigungu, eup_myeon_dong)
);

-- updateParentGrid()의 1차/2차 주소 매칭에서 사용합니다.
CREATE INDEX idx_region_grid_sido_sigungu
    ON ref.region_grid(sido, sigungu);

-- 기상청 원본 행정코드로 역추적하거나 재적재 검증할 때 사용합니다.
CREATE INDEX idx_region_grid_admin
    ON ref.region_grid(admin_code);

CREATE TRIGGER trg_region_grid_updated_at
    BEFORE UPDATE ON ref.region_grid
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
