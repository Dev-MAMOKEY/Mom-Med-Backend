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

## 2. Weather Rule Seed 적재

기본값은 자동 적재가 꺼져 있습니다. 필요할 때만 켭니다.

```powershell
$env:WEATHER_RULES_LOAD_ENABLED="true"
$env:WEATHER_RULES_SEED_PATH="seed/weather_rules_v0.2.json"
$env:WEATHER_RULES_VERSION="v0.2"

# DUR CSV 자동 적재가 켜져 있는 로컬 설정이면 함께 꺼둡니다.
$env:APP_ETL_DUR_ENABLED="false"

.\gradlew.bat bootRun
```

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

## 3. Region Grid xlsx 적재

```powershell
$env:REGION_GRID_LOAD_ENABLED="true"
$env:REGION_GRID_XLSX_PATH="기상청41_단기예보 조회서비스_오픈API활용가이드_2510/기상청41_단기예보 조회서비스_오픈API활용가이드_격자_위경도(2510).xlsx"
$env:APP_ETL_DUR_ENABLED="false"

.\gradlew.bat bootRun
```

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
