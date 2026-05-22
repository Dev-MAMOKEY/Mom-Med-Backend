# Slice 02 DUR Safety Smoke Test

이 문서는 Slice 02 DUR 1차 병용금기 검사 기능을 로컬에서 검증하는 절차입니다.
Slice 02는 외부 API를 호출하지 않고, HIRA DUR CSV를 PostgreSQL `ref` schema에 적재한 뒤
정규화 성분명 컬럼으로만 정확 매칭합니다.

## 1. DB 마이그레이션 실행

```bash
docker compose up -d
./gradlew bootRun
```

Spring Boot가 실행되면 Flyway가 `V006__create_dur_safety_tables.sql`을 적용합니다.
수동으로 확인하려면 PostgreSQL에 접속해 아래 SQL을 실행합니다.

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version = '006';
```

## 2. DUR CSV ETL 실행

기본 실행에서는 대용량 CSV를 자동 적재하지 않습니다.
필요할 때만 아래처럼 ETL 옵션을 켭니다.

```bash
./gradlew bootRun --args="--app.etl.dur.enabled=true"
```

기본 병용금기 CSV 경로는 우선 아래 파일을 찾습니다.

```text
data/_downloads/11983_ex/의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv
```

현재 저장소의 다운로드 폴더 구조가 다르면 애플리케이션이 아래 fallback 경로도 확인합니다.

```text
data/건강보험심사평가원_의약품안전사용서비스(DUR) 의약품 목록_20250601/의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv
```

경로를 직접 지정해야 하면 실행 옵션으로 넘깁니다.

```bash
./gradlew bootRun --args="--app.etl.dur.enabled=true --app.etl.dur.combo-path=data/_downloads/11983_ex/의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv"
```

## 3. 적재 Row Count 확인

```sql
SELECT count(*) FROM ref.dur_combo_contraindications;
SELECT count(*) FROM ref.dur_elderly_caution;
SELECT count(*) FROM ref.dur_elderly_nsaid_caution;
```

같은 CSV를 다시 적재해도 `source_row_hash` UNIQUE 제약과 `ON CONFLICT DO NOTHING` 때문에
중복 행이 늘어나지 않아야 합니다.

## 4. Safety API 테스트

`current_drugs`와 `new_drug`는 모두 `ref.drugs_master.item_seq`입니다.
아래 값은 예시이므로 로컬 DB에 적재된 ITEM_SEQ로 바꿔서 실행합니다.

```bash
curl -X POST http://localhost:8080/v1/safety/check \
  -H "Content-Type: application/json" \
  -d '{
    "parent_id": "parent-1",
    "age": 72,
    "current_drugs": ["<amlodipine ITEM_SEQ>"],
    "new_drug": "<itraconazole ITEM_SEQ>"
  }'
```

병용금기면 HTTP 409와 아래 구조를 기대합니다.

```json
{
  "error": "block",
  "verdict": {
    "decision": "BLOCK",
    "evidences": [
      {
        "source": "DUR",
        "type": "병용금기",
        "ingredient_a": "amlodipine",
        "ingredient_b": "itraconazole",
        "reason": "...",
        "gazette_no": "...",
        "gazette_date": "2025-06-01"
      }
    ]
  }
}
```

노인주의면 HTTP 200과 `decision: "WARN"`, 근거가 없으면 HTTP 200과 `decision: "ALLOW"`를 기대합니다.

## 5. 인덱스 사용 확인

병용금기 조회는 `LIKE '%...%'`가 아니라 정규화 성분명 두 컬럼의 equality 조건입니다.
아래 SQL의 실행 계획에서 `idx_dur_combo_pair`, `idx_dur_combo_norm_a`, `idx_dur_combo_norm_b` 중
인덱스 기반 계획이 나오는지 확인합니다.

```sql
EXPLAIN ANALYZE
SELECT *
FROM ref.dur_combo_contraindications
WHERE (ingredient_norm_a = 'amlodipine' AND ingredient_norm_b = 'itraconazole')
   OR (ingredient_norm_a = 'itraconazole' AND ingredient_norm_b = 'amlodipine');
```

노인주의 조회도 단일 정규화 성분명 equality 조건이어야 합니다.

```sql
EXPLAIN ANALYZE
SELECT *
FROM ref.dur_elderly_caution
WHERE ingredient_norm = 'zolpidem';
```

## 6. 테스트 코드 실행

```bash
./gradlew test
```

테스트는 전체 CSV 적재에 의존하지 않고 최소 fixture로 아래 회귀 시나리오를 검증합니다.

- amlodipine + itraconazole -> BLOCK
- 5-fluorouracil + tegafur -> BLOCK
- warfarin + aspirin -> ALLOW
- amlodipine + simvastatin -> ALLOW
- 72세 + 노인주의 약물 -> WARN
- 중복 raw 병용금기 행 -> evidence 1건으로 dedupe
- BLOCK -> HTTP 409
- WARN/ALLOW -> HTTP 200
