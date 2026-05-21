# Slice 06 v2 — 날씨 룰셋 + 격자 좌표 + 룩업 모듈

## Why (사용자 가치)
이 슬라이스 자체는 사용자 가치 없음 (인프라). Slice 07에서 매일 ETL+푸시를 돌릴 때 필요한 **정적 룰셋 + 격자 좌표 + 룩업 함수**를 준비.

## 의존성 (★ v1→v2 추가)
- Slice 00 v2: 4-schema
- Slice 04 v2: `app.patient_profiles` (nx, ny 채울 대상)
- **Slice 05 v2**: `app.patient_conditions` (룩업 시 disease_codes 가져옴) ★ v1엔 누락

## v1 → v2 변경
- DDL에 schema prefix
- WeatherAdvisory dataclass에 **`rule_id` 필드 추가** (slice 07 dedup용)
- severity CHECK 제약 추가
- 5번에서 룩업 키 정합성 강화
- 의존성 05 추가

## v2 → v2.1 변경 (cross-ref 검수 반영)
- `update_parent_grid()`에 **동(eup_myeon_dong) 우선 매칭 + 시·구 fallback** 추가
  - 도농복합지역(예: 경상북도 청도군)에서 산악/도심 격자 차이 반영
  - address_dong 컬럼이 dead data가 아니게 됨

## 외부 API 호출 명세
이 슬라이스에서는 **외부 API 호출 0건**. JSON·xlsx 로컬 적재.

## DB 적재

### Table: `ref.weather_rules`

```sql
CREATE TABLE ref.weather_rules (
    id                      BIGSERIAL PRIMARY KEY,
    rule_version            VARCHAR(20) NOT NULL,
    disease_code            VARCHAR(10) NOT NULL,
    disease_name            VARCHAR(100) NOT NULL,
    weather_alert           VARCHAR(50) NOT NULL,
    severity                VARCHAR(20) NOT NULL CHECK (severity IN ('관심','주의','경고','위험')),
    title                   VARCHAR(100) NOT NULL,
    message_template        TEXT NOT NULL,
    specific_drugs_to_note  JSONB NOT NULL DEFAULT '[]'::jsonb,
    patient_actions         JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_citations        JSONB NOT NULL DEFAULT '[]'::jsonb,
    rationale               TEXT,
    general_knowledge_used  BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by             VARCHAR(100),
    approved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(rule_version, disease_code, weather_alert)
);

CREATE INDEX idx_weather_rules_lookup ON ref.weather_rules(disease_code, weather_alert);
CREATE INDEX idx_weather_rules_review 
    ON ref.weather_rules(general_knowledge_used) 
    WHERE general_knowledge_used = TRUE AND approved_at IS NULL;
```

### Table: `ref.region_grid`

```sql
CREATE TABLE ref.region_grid (
    id              BIGSERIAL PRIMARY KEY,
    admin_code      VARCHAR(20),
    sido            VARCHAR(50) NOT NULL,
    sigungu         VARCHAR(50),
    eup_myeon_dong  VARCHAR(50),
    nx              SMALLINT NOT NULL,
    ny              SMALLINT NOT NULL,
    longitude       NUMERIC(10,6),
    latitude        NUMERIC(10,6),
    UNIQUE(sido, sigungu, eup_myeon_dong)
);
CREATE INDEX idx_region_grid_sido_sigungu ON ref.region_grid(sido, sigungu);
CREATE INDEX idx_region_grid_admin ON ref.region_grid(admin_code);
```

## 적재 작업

### 1. `weather_rules_v0.2.json` → `ref.weather_rules`

**파일**: `seed/weather_rules_v0.2.json` (32 룰)

```python
def load_weather_rules():
    with open('seed/weather_rules_v0.2.json', encoding='utf-8') as f:
        data = json.load(f)
    for rule in data['rules']:
        db.upsert("ref.weather_rules",
            unique=('rule_version', 'disease_code', 'weather_alert'),
            rule_version='v0.2',
            disease_code=rule['disease_code'],
            disease_name=rule['disease_name'],
            weather_alert=rule['weather_alert'],
            severity=rule['severity'],
            title=rule['title'],
            message_template=rule['message_template'],
            specific_drugs_to_note=rule['specific_drugs_to_note'],
            patient_actions=rule['patient_actions'],
            source_citations=rule['source_citations'],
            rationale=rule['rationale'],
            general_knowledge_used=rule['general_knowledge_used'],
        )
```

### 2. 격자 좌표 xlsx → `ref.region_grid`

**파일**: `기상청41_단기예보 조회서비스_오픈API활용가이드_2510/기상청41_단기예보 조회서비스_오픈API활용가이드_격자_위경도(2510).xlsx`

```python
import openpyxl

def load_region_grid():
    wb = openpyxl.load_workbook(XLSX_PATH, read_only=True, data_only=True)
    ws = wb.active
    batch = []
    for row in ws.iter_rows(min_row=2, values_only=True):
        gubun, admin_code, sido, sigungu, dong, nx, ny, *_lon, lon_dec, lat_dec, *_ = row
        if not sido or not nx:
            continue
        batch.append({
            'admin_code': admin_code,
            'sido': sido,
            'sigungu': sigungu or None,
            'eup_myeon_dong': dong or None,
            'nx': int(nx),
            'ny': int(ny),
            'longitude': float(lon_dec) if lon_dec else None,
            'latitude': float(lat_dec) if lat_dec else None,
        })
        if len(batch) >= 1000:
            db.bulk_insert_or_ignore("ref.region_grid", batch)
            batch = []
    if batch:
        db.bulk_insert_or_ignore("ref.region_grid", batch)
```

### 3. 부모 격자 좌표 채움 (slice 04와 통합)

★ v2.1: 동(eup_myeon_dong) 우선 매칭 → 시·구로 fallback (도농복합지역 격자 정확도 향상)

```python
def update_parent_grid(parent_id):
    parent = db.fetch_one("app.patient_profiles", parent_id=parent_id)
    
    # 1순위: 시·구·동 정확 매칭 (도농복합지역에서 산악/도심 격자 구분)
    grid = db.execute("""
        SELECT nx, ny FROM ref.region_grid
        WHERE sido = :sido 
          AND sigungu = :sigungu 
          AND eup_myeon_dong = :dong
        LIMIT 1
    """, {
        "sido": parent.address_sido,
        "sigungu": parent.address_sigungu,
        "dong": parent.address_dong,
    }).first()
    
    # 2순위: 동 매칭 실패 시 시·구만 (예: 동 정보 미입력 또는 region_grid 누락)
    if not grid:
        grid = db.execute("""
            SELECT nx, ny FROM ref.region_grid
            WHERE sido = :sido AND sigungu = :sigungu
            LIMIT 1
        """, {"sido": parent.address_sido, "sigungu": parent.address_sigungu}).first()
    
    if grid:
        db.execute("""
            UPDATE app.patient_profiles SET nx = :nx, ny = :ny WHERE parent_id = :pid
        """, {"nx": grid.nx, "ny": grid.ny, "pid": parent_id})
```

또는 slice 04의 POST/PATCH 트리거에서 자동 호출.

## WeatherDiseaseAdvisor 룩업 모듈 (PRD 모듈 ⑦ 운영 코어)

```python
@dataclass
class WeatherAdvisory:
    rule_id: int                        # ★ v2 신규 — slice 07의 dedup용
    disease_code: str
    disease_name: str
    weather_alert: str
    severity: str
    title: str
    message: str
    patient_actions: list[str]
    specific_drugs_to_note: list[str]
    source_citations: list[dict]
    requires_review: bool


def lookup_rules(disease_codes: list[str], weather_alerts: list[str]) -> list[WeatherAdvisory]:
    """
    부모 기저질환 × 오늘 활성 기상특보 → 룰 매칭 (정확 매칭).
    """
    if not disease_codes or not weather_alerts:
        return []
    
    rules = db.execute("""
        SELECT id, disease_code, disease_name, weather_alert, severity, title,
               message_template, patient_actions, specific_drugs_to_note,
               source_citations, approved_at, general_knowledge_used
        FROM ref.weather_rules
        WHERE disease_code = ANY(:codes::text[])
          AND weather_alert = ANY(:alerts::text[])
    """, {"codes": disease_codes, "alerts": weather_alerts})
    
    advisories = []
    for r in rules:
        advisories.append(WeatherAdvisory(
            rule_id=r.id,
            disease_code=r.disease_code,
            disease_name=r.disease_name,
            weather_alert=r.weather_alert,
            severity=r.severity,
            title=r.title,
            message=r.message_template,           # 호칭 치환은 slice 07
            patient_actions=r.patient_actions,
            specific_drugs_to_note=r.specific_drugs_to_note,
            source_citations=r.source_citations,
            requires_review=(r.approved_at is None and r.general_knowledge_used),
        ))
    return advisories


def get_parent_conditions(parent_id) -> list[str]:
    """slice 05의 정규화 테이블에서 활성 질환 코드 list 반환."""
    rows = db.execute("""
        SELECT disease_code FROM app.patient_conditions
        WHERE parent_id = :pid AND deleted_at IS NULL
    """, {"pid": parent_id})
    return [r.disease_code for r in rows]
```

## API 계약

```
GET /v1/parents/{parent_id}/weather-advisory?date=YYYY-MM-DD
  
  내부 흐름:
    1. get_parent_conditions(parent_id) → disease_codes (slice 05의 JOIN)
    2. (slice 07에서 채울) 오늘 활성 기상특보 조회
    3. lookup_rules(disease_codes, alerts) 호출
    4. 알람 피로 방지 (slice 07)
  
  Response 200:
  {
    "parent_id": "...",
    "date": "2026-08-15",
    "weather_alerts_today": ["폭염경보"],
    "advisories": [
      {
        "rule_id": 5,                     // ★ v2 신규
        "disease_code": "I10",
        "disease_name": "본태성 고혈압",
        "weather_alert": "폭염경보",
        "severity": "위험",
        "title": "어머님 폭염경보 — 탈수·어지럼 응급주의",
        "message": "어머님 오늘 폭염경보예요. ...",
        "patient_actions": ["외출 금지", "수분 자주"],
        "drugs": ["이뇨제", "CCB"],
        "source_citations": [...],
        "requires_review": true
      }
    ]
  }

GET /v1/admin/weather-rules?status=needs_review
  Response 200: { total, rules: [...] }

POST /v1/admin/weather-rules/{id}/approve
  Request: { "approved_by": "약사 김OO", "adjusted_message": "..." }
  Response 200: { "approved_at": "...", ... }
```

## 수락 기준

- [ ] `ref.weather_rules` 32행 적재 + UNIQUE 제약
- [ ] 17행 `general_knowledge_used=TRUE` 검수 우선 큐 (slice 07에서 표시)
- [ ] `ref.region_grid` 26K+ 행정구역 적재
- [ ] 대구광역시 + 중구 → nx=89, ny=90 검증
- [ ] 서울특별시 + 종로구 → nx=60, ny=127 검증
- [ ] slice 04에서 등록된 부모(대구 중구) → `app.patient_profiles.nx/ny` 자동 채움
- [ ] **동 매칭 우선** (v2.1): 부모 주소가 "대구 중구 성내동"이면 동 정확매칭, 동 없는 행은 시·구 fallback
- [ ] `lookup_rules(["I10","E11"], ["폭염경보"])` → 2개 advisory, **각각 `rule_id` 필드 포함**
- [ ] 룰 없는 조합 → 빈 배열 (오류 아님)
- [ ] 의료진 검수 API: `POST /admin/weather-rules/{id}/approve` 후 `approved_at` 채워짐
- [ ] slice 05의 `app.patient_conditions` JOIN으로 정상 동작 (의존성 정합)

## 회귀 자산
- `seed/weather_rules_v0.2.json` (32 룰)
- 기상청 격자 xlsx (26K 행)

## 환경변수
- 없음

## 범위 밖
- 기상청 API 호출 (slice 07)
- 자체 임계값 판정 (slice 07)
- 매일 ETL 잡 (slice 07)
- 자녀 푸시 인프라 (slice 07 + 별도 PRD)
- 알람 피로 방지 (slice 07)
- 어투 변환 LLM (Phase 2)
- Phase 2 룰셋 확장 (미세먼지·황사·F03 치매)

## Smoke Test
```bash
# 룰셋 적재 확인
psql $DATABASE_URL -c "SELECT count(*) FROM ref.weather_rules;"  # 32

# 격자 좌표
psql $DATABASE_URL -c "SELECT nx, ny FROM ref.region_grid WHERE sido='대구광역시' AND sigungu='중구';"
# 89, 90

# 룩업 호출 (parent_id 시드 필요)
curl "http://localhost:8000/v1/parents/${PARENT_ID}/weather-advisory?simulate_alert=폭염경보" | jq '.advisories[0].rule_id'
# 정수 반환

# 검수 큐
curl "http://localhost:8000/v1/admin/weather-rules?status=needs_review" | jq '.total'  # 17
```
