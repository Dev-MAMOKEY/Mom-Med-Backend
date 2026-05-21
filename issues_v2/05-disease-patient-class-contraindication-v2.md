# Slice 05 v2 — 기저질환 + 환자분류 금기

## Why (사용자 가치)
자녀가 어머니 **기저질환을 등록**해두면, 약 추가 시 그 질환에 위험한 약을 자동 경고. 식약처 NB의 "심한 간장애 환자 금기" 같은 자유 문장을 LLM이 추출해 부모 질환과 교차 매칭. PRD 기능 3 완성.

## 의존성 (★ v1→v2 추가)
- Slice 00 v2: 4-schema, `call_llm`, `verify_quotes`
- **Slice 02 v2**: `ref.dur_age_contraindication`·`ref.dur_pregnancy_contraindication` 테이블 (이 슬라이스에서 적재) ★ v1엔 누락
- Slice 03 v2: NB 추출 파이프라인 (이 슬라이스에서 `entry_type='patient_class'` 추가)
- Slice 04 v2: `app.patient_profiles`

## v1 → v2 변경
- DDL에 schema prefix
- `patient_conditions` **정규화 테이블 확정** (JSONB 옵션 제거)
- 02 의존성 명시 추가
- LLM: Gemini 2.5 Flash Lite (slice 03과 동일)
- KCD 매핑 사전 명시 (10개 케이스로 확장)
- `matches_kcd_range` 의사코드 추가
- `app.patient_profiles`는 ALTER 없이 그대로 (conditions는 별도 테이블)

## 외부 API 호출 명세

### API 1 — HIRA 질병정보서비스 (질병코드 검증)
- **sno**: 12904
- **endpoint**: `http://apis.data.go.kr/B551182/diseaseInfoService1/getDissNameCodeList1`
- **method**: GET · XML 응답 (JSON 미지원)

| 파라미터 | 필수 | 값 출처 | 예시 |
|---|---|---|---|
| `serviceKey` | ✅ | env.HIRA_API_KEY | f204... |
| `numOfRows` | - | 5 | 5 |
| `pageNo` | - | 1 | 1 |
| `sickType` | ✅ | "1" (3단) 또는 "2" (4단) | 1 |
| `medTp` | ✅ | "1" (양방) | 1 |
| `diseaseType` | ✅ | "SICK_CD" 또는 "SICK_NM" | SICK_CD |
| `searchText` | ✅ | 자녀 입력 | E11 |

```bash
curl -s "http://apis.data.go.kr/B551182/diseaseInfoService1/getDissNameCodeList1?serviceKey=${HIRA_API_KEY}&numOfRows=5&pageNo=1&sickType=1&medTp=1&diseaseType=SICK_CD&searchText=E11"
```

**응답 (XML)**:
```xml
<response>
  <body>
    <items>
      <item>
        <sickCd>E11</sickCd>
        <sickNm>2형 당뇨병</sickNm>
        <sickEngNm>Type 2 diabetes mellitus</sickEngNm>
      </item>
    </items>
  </body>
</response>
```

| 필드 | 사용처 |
|---|---|
| `sickCd` | `ref.disease_master.sick_cd` 검증 |
| `sickNm` | 사용자에게 보일 한글명 |

### API 2 — Gemini 2.5 Flash Lite (NB 환자분류 추출)
Slice 03의 LLM 호출에 환자분류 entry 추가 지시. 같은 `call_llm` 유틸 재사용. 프롬프트 확장만.

## CSV 적재 (Slice 02에서 테이블 생성, 이 슬라이스에서 적재)

```python
load_csv("data/_downloads/11983_ex/.../연령금기 품목리스트 2025.6.csv", 
         table="ref.dur_age_contraindication", encoding="cp949",
         column_mapping={
             "성분명": "ingredient_raw",
             "성분코드": "gnl_nm_cd",
             "특정연령": "age_threshold",
             "특정연령단위": "age_unit",
             "연령처리조건": "condition",
             "상세정보": "detail",
             "고시일자": "gazette_date"
         },
         generate={"ingredient_norm": lambda r: normalize_drug_name(r["성분명"])})
# 2,905행

load_csv("data/_downloads/11983_ex/.../임부금기 품목리스트 2025.6.csv", 
         table="ref.dur_pregnancy_contraindication", ...)
# 19,015행
```

### `ref.disease_master` 적재 (HIRA 11984 CSV)

⚠️ 11984는 CSV 다운로드 필요 (`data/_downloads/11984.bin`).

```sql
CREATE TABLE ref.disease_master (
    sick_cd             VARCHAR(10) PRIMARY KEY,
    sick_nm             VARCHAR(200) NOT NULL,
    sick_eng_nm         VARCHAR(300),
    complete_code_flag  CHAR(1),
    main_diagnosis      CHAR(1),
    infectious_grade    VARCHAR(20),
    sex_restriction     CHAR(1),
    age_max             INT,
    age_min             INT,
    yang_han_type       VARCHAR(20),
    source_csv_date     VARCHAR(20) NOT NULL DEFAULT '2024.11',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_disease_master_nm_trgm ON ref.disease_master USING gin (sick_nm gin_trgm_ops);
```

**CSV → DB 매핑**:

| CSV | DB |
|---|---|
| 상병기호 | sick_cd |
| 한글명 | sick_nm |
| 영문명 | sick_eng_nm |
| 완전코드구분 | complete_code_flag |
| 주상병사용구분 | main_diagnosis |
| 법정감염병구분 | infectious_grade |
| 성별구분 | sex_restriction |
| 상한연령 | age_max (정수 변환) |
| 하한연령 | age_min |
| 양한방구분 | yang_han_type |

## DB 적재 (이 슬라이스 신규)

### Table: `app.patient_conditions` (★ 정규화 테이블 확정)

```sql
CREATE TABLE app.patient_conditions (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    disease_code    VARCHAR(10) NOT NULL REFERENCES ref.disease_master(sick_cd),
    diagnosed_at    DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- 활성 동일 질환 중복 방지 (partial UNIQUE)
CREATE UNIQUE INDEX uniq_pcond_active 
    ON app.patient_conditions(parent_id, disease_code) 
    WHERE deleted_at IS NULL;
CREATE INDEX idx_pcond_parent_active 
    ON app.patient_conditions(parent_id) 
    WHERE deleted_at IS NULL;
```

`derived.nb_interactions`는 Slice 03에서 이미 `entry_type='patient_class'` 분기 컬럼 포함. ALTER 불필요.

## 환자분류 → KCD 매핑 사전 (LLM 프롬프트 확장)

Slice 03 프롬프트에 다음 항목 추가:

```text
ADDITIONAL TASK (v2 — slice 05 확장):
본문에 "이 약 금기" 또는 "신중 투여" 환자 분류가 명시되면 별도 entry로:

{
  "entry_type": "patient_class",
  "patient_class_text": "<원문 그대로, 예: '심한 간장애 환자'>",
  "patient_class_kcd": "<AI 추론 KCD 범위, 예: 'K70-K77'>",
  "risk_level": "<금기|신중투여>",
  "source_quote": "<원문 byte-for-byte>"
}

KCD 매핑 가이드 (10개 케이스):
- "간장애 환자" → K70-K77
- "신장(콩팥)장애 환자" → N17-N19
- "심장기능저하 환자" → I50, I20-I25
- "소화성궤양 환자" → K25-K27
- "혈액 이상 환자" → D60-D64, D70-D77
- "심한 고혈압 환자" → I10-I15
- "당뇨 환자" → E10-E14
- "갑상선 질환 환자" → E00-E07
- "임산부" → 별도 pregnancy 플래그
- "과민증 환자" → 전체 약물 알레르기 — patient_allergies로 처리
```

## SafetyJudge 확장 (모듈 ④ 완전판)

Slice 03의 `judge_full()`에 환자분류·연령·임부 검사 추가:

```python
def judge_full(parent: PatientContext, current_drugs, new_drug) -> SafetyVerdict:
    evidences = []
    
    # 1차 DUR (slice 02)
    # 2차 NB drug_drug (slice 03)
    # ... 위 두 단계는 그대로 ...
    
    # 3차 (이 슬라이스): NB patient_class + DUR age + pregnancy + allergy
    evidences.extend(check_patient_class_contraindication(parent, new_drug))
    evidences.extend(check_age_contraindication(parent, new_drug))
    if parent.is_pregnant:
        evidences.extend(check_pregnancy_contraindication(new_drug))
    evidences.extend(check_allergy(parent, new_drug))  # patient_allergies 활용
    
    # 통합 판정 + dedupe (v3·v6에서 이미 정의)
    return integrate_decision(evidences)


def check_patient_class_contraindication(parent, new_drug) -> list[Evidence]:
    # 부모의 활성 기저질환
    conditions = db.execute("""
        SELECT disease_code FROM app.patient_conditions
        WHERE parent_id = :pid AND deleted_at IS NULL
    """, {"pid": parent.parent_id})
    parent_kcd_set = {c.disease_code for c in conditions}
    
    # 새 약의 NB patient_class entries
    nb_class_rows = db.execute("""
        SELECT * FROM derived.nb_interactions
        WHERE item_seq = :item AND entry_type = 'patient_class'
    """, {"item": new_drug.item_seq})
    
    evidences = []
    for row in nb_class_rows:
        if matches_kcd_range(row.patient_class_kcd, parent_kcd_set):
            evidences.append(Evidence(
                source="NB_patient_class",
                risk_level=row.risk_level,
                patient_class=row.patient_class_text,
                matched_to=list(parent_kcd_set & expand_kcd_range(row.patient_class_kcd)),
                quote=row.source_quote,
                item_seq_source=new_drug.item_seq,
            ))
    return evidences


def matches_kcd_range(rule_kcd: str, parent_set: set[str]) -> bool:
    """
    rule_kcd 예: "K70" 또는 "K70-K77" 또는 "I20-I25, I50"
    parent_set 예: {"E11", "K25"}
    """
    expanded = expand_kcd_range(rule_kcd)  # → {"K70", "K71", ..., "K77"}
    # 부모 코드의 prefix 확장 매칭 (E11이 E11-E14 범위에 들어가는지 등)
    for parent_code in parent_set:
        if parent_code in expanded:
            return True
        # prefix 매칭 (K2가 K25에 매칭 등)
        for ekcd in expanded:
            if parent_code.startswith(ekcd) or ekcd.startswith(parent_code):
                return True
    return False


def expand_kcd_range(rule_kcd: str) -> set[str]:
    """K70-K77 → {K70, K71, K72, ..., K77}"""
    # 구현: 정규식 파싱
    ...


def check_age_contraindication(parent, new_drug) -> list[Evidence]:
    rows = db.execute("""
        SELECT * FROM ref.dur_age_contraindication
        WHERE ingredient_norm = :n
    """, {"n": new_drug.main_ingr_norm})
    return [
        Evidence(source="DUR_age", risk_level="동시투여피해야함", ...)
        for v in rows if applies_age(parent.age, v.age_threshold, v.condition)
    ]


def check_pregnancy_contraindication(new_drug) -> list[Evidence]:
    rows = db.execute("""
        SELECT * FROM ref.dur_pregnancy_contraindication
        WHERE ingredient_norm = :n
    """, {"n": new_drug.main_ingr_norm})
    return [Evidence(source="DUR_pregnancy", ...) for v in rows]


def check_allergy(parent, new_drug) -> list[Evidence]:
    # slice 04의 patient_allergies 활용
    allergies = db.execute("""
        SELECT * FROM app.patient_allergies
        WHERE parent_id = :pid AND deleted_at IS NULL
          AND allergen_type = 'drug'
          AND allergen_norm IS NOT NULL
    """, {"pid": parent.parent_id})
    
    evidences = []
    for allergy in allergies:
        if allergy.allergen_norm == new_drug.main_ingr_norm:
            evidences.append(Evidence(
                source="ALLERGY",
                risk_level="동시투여피해야함",  # 알레르기는 항상 최고 강도
                allergen=allergy.allergen_name,
                severity=allergy.severity,
            ))
    return evidences
```

## API 계약 (이 슬라이스가 노출)

```
POST /v1/parents/{parent_id}/conditions
  Request: { "disease_code": "I10" } 또는 { "disease_name": "고혈압" }
  
  내부 흐름:
    1. searchText로 HIRA API 호출 → sick_cd 검증
    2. ref.disease_master 캐시 (CSV 적재돼 있음)
    3. app.patient_conditions insert
  
  Response 201:
  { "id": 1, "disease_code": "I10", "disease_name": "본태성 고혈압" }

GET /v1/parents/{parent_id}/conditions
DELETE /v1/parents/{parent_id}/conditions/{id}

POST /v1/safety/check  (slice 02·03 확장)
  Response evidences에 source="NB_patient_class", "DUR_age", "DUR_pregnancy", "ALLERGY" 추가됨
```

## End-to-End 시나리오

1. 자녀가 어머니 기저질환 등록:
   ```
   POST /v1/parents/{id}/conditions { "disease_code": "K25" }   # 위궤양
   POST /v1/parents/{id}/conditions { "disease_code": "N18" }   # 만성 신장병
   ```
2. NSAID 약 추가 시도:
   ```
   POST /v1/parents/{id}/medications { "item_seq": "<이부프로펜>" }
   ```
3. 응답 — NB의 "소화성궤양 환자" + 부모 K25 매칭:
   ```json
   {
     "safety_check": {
       "decision": "WARN",
       "evidences": [
         {
           "source": "NB_patient_class",
           "patient_class": "소화성궤양 환자",
           "matched_to": ["K25"],
           "quote": "다음 환자에는 신중히 투여할 것: ... 소화성궤양 환자 ...",
           "risk_level": "신중투여"
         }
       ]
     }
   }
   ```

## 수락 기준

- [ ] `ref.disease_master` 47,798행 적재 + trgm 인덱스
- [ ] `ref.dur_age_contraindication` 2,905행, `ref.dur_pregnancy_contraindication` 19,015행 적재
- [ ] `app.patient_conditions` 테이블 + UNIQUE 제약 + FK 생성
- [ ] HIRA 12904 API 실호출 → I10·E11·K25 모두 표준명 반환
- [ ] 회귀:
  - 부모 K25 + 타이레놀500 추가 → NB "소화성궤양 환자" entry 매칭으로 WARN
  - 부모 72세 + 12세 미만 금기 약 → 미적용 (age != child)
  - 부모 임신 ✓ + 임부금기 약 → BLOCK
  - 부모 페니실린 알레르기 + amoxicillin (페니실린 계열) → BLOCK
- [ ] KCD 매핑 10개 케이스 모두 통과 (단위 테스트)
- [ ] `app.patient_conditions` JOIN으로 slice 06·07·08에서 conditions 가져옴 (정규화 테이블 확정 검증)

## 회귀 자산
- `nb_amlodipine.json` (환자분류 entry 거의 없음 — 대조)
- `nb_warfarin.json` (환자분류 entry 있음)
- 새로 만들 골든: 타이레놀500 NB 환자분류 추출 (6개 환자군: 과민증·소화성궤양·혈액이상·간장애·신장장애·심장기능저하)

## 환경변수
- `HIRA_API_KEY`
- `GEMINI_API_KEY` (NB 재추출 시)
- `MFDS_API_KEY` (slice 01 재호출)

## 범위 밖
- 자가 진단 입력 (반드시 의료진 진단 코드)
- F03 치매·경도인지장애 약 관리 능력 영향 (별도 PRD)
- 사용자에게 보일 KCD ↔ 한글 변환 (`ref.disease_master.sick_nm` 활용)

## Smoke Test
```bash
# 1) 기저질환 등록
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/conditions" \
  -d '{"disease_code":"K25"}'

# 2) NSAID 추가 시도 → WARN 기대
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/medications" \
  -d '{"item_seq":"<NSAID>","source":"otc"}' | jq '.safety_check'

# 3) 알레르기 + 페니실린 계열 약 → BLOCK
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/medications" \
  -d '{"item_seq":"<amoxicillin>","source":"prescription"}'
# 기대: HTTP 409
```
