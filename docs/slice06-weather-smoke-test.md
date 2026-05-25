# Slice 06 Weather Rules Smoke Test

## 목적

Slice 06은 외부 API를 호출하지 않고, 로컬 정적 데이터만 준비합니다.

- `seed/weather_rules_v0.2.json` → `ref.weather_rules`
- 기상청 격자 xlsx → `ref.region_grid`
- 부모 기저질환 코드 + 활성 기상특보 → weather advisory 조회

## 1. DB 마이그레이션

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

애플리케이션 시작 시 Flyway가 `V019`, `V020`을 적용합니다.

확인 SQL:

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'ref'
  AND table_name IN ('weather_rules', 'region_grid');
```

## 2. 테스트 전 적재 방법

### 방법 A. 팀 공유용 추천 방식: `seed-weather` profile

팀원은 환경변수를 여러 개 직접 입력할 필요 없이 `seed-weather` profile만 추가하면 됩니다.
이 profile은 Git에 올라가는 공용 설정이며, 민감정보는 포함하지 않습니다.

IntelliJ:

1. 우측 상단 `BackendApplication` 실행 설정 클릭
2. `Edit Configurations...`
3. `Active profiles`에 아래 값 입력

```text
local,seed-weather
```

4. 실행

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="local,seed-weather"
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
SPRING_PROFILES_ACTIVE=local,seed-weather ./gradlew bootRun
```

이 방식으로 실행하면 애플리케이션 시작 시 아래 두 적재가 자동으로 수행됩니다.

- `seed/weather_rules_v0.2.json` → `ref.weather_rules`
- `기상청41_단기예보...격자_위경도(2510).xlsx` → `ref.region_grid`

기상청 xlsx가 Apache POI의 zip bomb 보호 기준에 걸릴 수 있으므로 `seed-weather` profile은
`min-inflate-ratio: 0.001`을 기본으로 사용합니다. 팀원이 파일 위치만 다르면 profile은 유지하고
`REGION_GRID_XLSX_PATH`만 각자 환경변수로 덮어쓰면 됩니다.

확인 SQL:

```sql
SELECT count(*) FROM ref.weather_rules;
-- 기대: 32

SELECT count(*)
FROM ref.weather_rules
WHERE general_knowledge_used = TRUE
  AND approved_at IS NULL;
-- 기대: 17
```

### 방법 B. 일회성 환경변수 방식

파일 위치를 바꿔서 테스트해야 할 때만 사용합니다.

PowerShell:

```powershell
$env:WEATHER_RULES_LOAD_ENABLED="true"
$env:WEATHER_RULES_SEED_PATH="seed/weather_rules_v0.2.json"
$env:WEATHER_RULES_VERSION="v0.2"
$env:REGION_GRID_LOAD_ENABLED="true"
$env:REGION_GRID_XLSX_PATH="기상청41_단기예보 조회서비스_오픈API활용가이드_2510/기상청41_단기예보 조회서비스_오픈API활용가이드_격자_위경도(2510).xlsx"
$env:REGION_GRID_MIN_INFLATE_RATIO="0.001"
$env:APP_ETL_DUR_ENABLED="false"

.\gradlew.bat bootRun
```

## 3. Region Grid 적재 확인

확인 SQL:

```sql
SELECT count(*) FROM ref.region_grid;
-- 기대: 26000행 이상

SELECT nx, ny
FROM ref.region_grid
WHERE sido = '대구광역시'
  AND sigungu = '중구'
LIMIT 5;
-- 기대: nx=89, ny=90 포함

SELECT nx, ny
FROM ref.region_grid
WHERE sido = '서울특별시'
  AND sigungu = '종로구'
LIMIT 5;
-- 기대: nx=60, ny=127 포함
```

## 4. 룩업 API 테스트

Slice 06은 기상청 API를 호출하지 않으므로 `simulate_alert`로 활성 특보를 넣어 테스트합니다.

```bash
curl "http://localhost:8080/v1/parents/{PARENT_ID}/weather-advisory?simulate_alert=폭염경보"
```

Postman:

- Method: `GET`
- URL: `http://localhost:8080/v1/parents/{PARENT_ID}/weather-advisory`
- Query Params:
  - `simulate_alert`: `폭염경보`

기대 응답:

```json
{
  "success": true,
  "code": 200,
  "message": "OK",
  "data": {
    "parent_id": "...",
    "weather_alerts_today": ["폭염경보"],
    "advisories": [
      {
        "rule_id": 1,
        "disease_code": "I10",
        "weather_alert": "폭염경보",
        "requires_review": false
      }
    ]
  }
}
```

## 5. 의료진 검수 API

검수 대기 큐:

```bash
curl "http://localhost:8080/v1/admin/weather-rules?status=needs_review"
```

Postman:

- Method: `GET`
- URL: `http://localhost:8080/v1/admin/weather-rules`
- Query Params:
  - `status`: `needs_review`

승인:

```bash
curl -X POST "http://localhost:8080/v1/admin/weather-rules/{RULE_ID}/approve" \
  -H "Content-Type: application/json" \
  -d '{"approved_by":"약사 김OO","adjusted_message":"보정 메시지"}'
```

확인 SQL:

```sql
SELECT id, approved_by, approved_at
FROM ref.weather_rules
WHERE id = {RULE_ID};
```

## 6. 인덱스 사용 확인

```sql
EXPLAIN ANALYZE
SELECT id, disease_code, weather_alert
FROM ref.weather_rules
WHERE disease_code IN ('I10', 'E11')
  AND weather_alert IN ('폭염경보');
```

`idx_weather_rules_lookup` 또는 해당 인덱스 기반 스캔이 보이면 정상입니다.

## 7. 테스트 실행

```powershell
.\gradlew.bat test
```

검증하는 내용:

- weather rule JSON seed 파싱
- region grid xlsx 파싱
- 대구 중구 동 우선 매칭
- 서울 종로구 시/구 fallback 매칭
- `lookupRules(["I10","E11"], ["폭염경보"])` 결과에 `rule_id` 포함
- 검수 승인 후 `approved_at` 채움
