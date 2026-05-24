# Slice 03 NB 추출 Smoke Test

이 문서는 Slice 03의 NB AI 추출 파이프라인을 로컬에서 확인하는 절차입니다.
NB 추출은 식약처 `ref.drugs_master.nb_doc_data` 원문을 Gemini로 구조화하고,
`HallucinationVerifier` 검증을 통과한 interaction만 `derived.nb_interactions`에 저장합니다.

## 1. 사전 준비

### DB와 애플리케이션 실행

```bash
docker compose up -d
./gradlew bootRun
```

로컬 PostgreSQL을 직접 사용할 경우에는 `.env`의 `DB_URL`, `DB_USER`, `DB_PASSWORD` 또는
`DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` 값을 실행 환경에 맞게 맞춥니다.

### 필수 환경변수

```env
GEMINI_API_KEY=your-google-ai-studio-key
DATABASE_URL=jdbc:postgresql://localhost:5432/mom_med
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-local-password
```

실제 API Key와 비밀번호는 `.env`에만 두고 Git에는 올리지 않습니다.

## 2. Flyway 마이그레이션 확인

애플리케이션 시작 시 Flyway가 자동 실행됩니다. 수동으로 확인하려면 다음 SQL을 실행합니다.

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version IN ('007', '008')
ORDER BY version;
```

기대 결과:

- `007 create nb extractions` 성공
- `008 create nb interactions` 성공

테이블 확인:

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'derived'
  AND table_name IN ('nb_extractions', 'nb_interactions')
ORDER BY table_name;
```

## 3. 테스트용 NB_DOC_DATA 준비

이미 Slice 01에서 실제 식약처 API로 `ref.drugs_master`가 채워져 있으면 이 단계는 생략할 수 있습니다.
로컬 smoke test만 빠르게 하려면 fixture 원문을 약 마스터에 넣습니다.

```sql
INSERT INTO ref.drugs_master (
    item_seq,
    item_name,
    entp_name,
    atc_code,
    main_ingr_en,
    main_ingr_norm,
    nb_doc_data,
    source_change_date,
    refreshed_at
)
VALUES (
    '200610660',
    '노바스크정5밀리그람',
    'test',
    'C08CA01',
    'Amlodipine Besylate',
    'amlodipine',
    '<nb_amlodipine.txt 내용을 붙여넣기>',
    DATE '2026-05-23',
    now()
)
ON CONFLICT (item_seq) DO UPDATE SET
    nb_doc_data = EXCLUDED.nb_doc_data,
    source_change_date = EXCLUDED.source_change_date,
    refreshed_at = now();
```

심바스타틴 safety/check를 확인하려면 비교 대상 약도 추가합니다.

```sql
INSERT INTO ref.drugs_master (
    item_seq,
    item_name,
    entp_name,
    atc_code,
    main_ingr_en,
    main_ingr_norm,
    refreshed_at
)
VALUES (
    'SIMV001',
    '심바스타틴 테스트약',
    'test',
    'C10AA01',
    'Simvastatin',
    'simvastatin',
    now()
)
ON CONFLICT (item_seq) DO UPDATE SET
    atc_code = EXCLUDED.atc_code,
    main_ingr_norm = EXCLUDED.main_ingr_norm,
    refreshed_at = now();
```

## 4. NB 추출 트리거

```bash
curl -X POST http://localhost:8080/v1/drugs/200610660/extract-nb
```

기대 응답 예시:

```json
{
  "item_seq": "200610660",
  "verified": true,
  "interaction_count": 39,
  "from_cache": false
}
```

두 번째로 같은 요청을 보내면 DB 캐시를 사용하므로 `from_cache=true`가 나와야 합니다.

## 5. DB 저장 결과 확인

```sql
SELECT item_seq, verified, token_input, token_output, cost_usd
FROM derived.nb_extractions
WHERE item_seq = '200610660'
ORDER BY id DESC
LIMIT 1;
```

```sql
SELECT partner_drug_ko, partner_drug_en, partner_drug_norm, risk_level, is_drug_group
FROM derived.nb_interactions
WHERE item_seq = '200610660'
  AND entry_type = 'drug_drug'
ORDER BY id;
```

노바스크 골든 기준 기대 개수:

```sql
SELECT count(*)
FROM derived.nb_interactions
WHERE item_seq = '200610660'
  AND entry_type = 'drug_drug';
```

기대 결과: `39`

핵심 ground-truth 확인:

```sql
SELECT partner_drug_ko, partner_drug_en, risk_level
FROM derived.nb_interactions
WHERE item_seq = '200610660'
  AND (
      partner_drug_ko IN ('심바스타틴', '이트라코나졸', '케토코나졸', '시클로스포린', '클래리트로마이신', '단트롤렌', '자몽')
      OR partner_drug_en IN ('simvastatin', 'itraconazole', 'ketoconazole', 'cyclosporine', 'clarithromycin', 'dantrolene')
  )
ORDER BY partner_drug_ko;
```

## 6. Safety Check에서 NB WARN 확인

Slice 02 DUR에서는 암로디핀 + 심바스타틴이 음성대조입니다.
Slice 03에서는 NB interaction이 이를 회수하여 WARN을 반환해야 합니다.

```bash
curl -X POST http://localhost:8080/v1/safety/check \
  -H "Content-Type: application/json" \
  -d '{
    "parent_id": "00000000-0000-0000-0000-000000000001",
    "age": 60,
    "current_drugs": ["200610660"],
    "new_drug": "SIMV001"
  }'
```

기대 응답:

```json
{
  "decision": "WARN",
  "evidences": [
    {
      "source": "NB",
      "risk_level": "주의",
      "partner_drug": "심바스타틴",
      "item_seq_source": "200610660"
    }
  ]
}
```

## 7. 금기 목록 조회

```bash
curl http://localhost:8080/v1/drugs/200610660/contraindications
```

기대 결과:

- `item_seq = "200610660"`
- `verified = true`
- `interactions`에 검증된 NB interaction 목록 포함

## 8. Metrics 확인

```bash
curl http://localhost:8080/metrics
```

기대 출력 예시:

```text
nb_extraction_token_input_total{model="gemini-2.5-flash-lite"} 3500
nb_extraction_token_output_total{model="gemini-2.5-flash-lite"} 6200
nb_extraction_cost_usd_total{model="gemini-2.5-flash-lite"} 0.001
```

## 9. 테스트 실행

```bash
./gradlew test
```

Slice 03에서 확인하는 주요 테스트:

- NB_DOC_DATA 전처리: CDATA, 태그, entity 제거
- 골든 fixture: 노바스크 39건, 와파린 46건
- NB 캐시 hit: 같은 `item_seq` 두 번째 호출 시 Gemini 호출 0회
- SafetyJudge 통합: 암로디핀 + 심바스타틴 WARN
- 약물군 매칭: `CYP3A4 저해제` entry가 `J02AC*` ATC 약과 매칭

## 10. 주의사항

- `source_quote`가 정제된 NB_DOC_DATA에 존재하지 않으면 `verified=false`로 저장되고 사용자 응답에는 노출하지 않습니다.
- LLM 비용 절감을 위해 캐시 키는 `item_seq + drug_change_date + llm_model + prompt_version` 조합입니다.
- `derived.nb_interactions.partner_drug_norm`은 단일 약물 매칭 키입니다. 영문명이 있으면 영문 정규화 값을 우선 저장합니다.
- 약물군은 `DrugGroupDictionary.GROUP_TO_ATC_PREFIX`의 ATC prefix로 매칭합니다.
