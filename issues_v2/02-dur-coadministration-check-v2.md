# Slice 02 v2 — DUR 1차 병용금기 검사 (정확 매칭)

## Why (사용자 가치)
부모님 약 2개 이상의 **병용금기 약쌍**을 한국 HIRA DUR 명단으로 즉시(~10ms) 차단. "이거랑 이거 같이 드시면 안 돼요!" 1차 빠른 안전망.

## 의존성
- Slice 00 v2: 4-schema 인프라, `normalize_drug_name`
- Slice 01 v2: `ref.drugs_master.main_ingr_norm` 컬럼

## v1 → v2 변경
- **DUR 매칭을 `LIKE '%X%'` → `=` 정확 매칭**으로 변경 (인덱스 활용)
- DDL에 schema prefix (`ref.dur_*`)
- Idempotency UNIQUE 제약 추가 (재적재 시 중복 방지)
- warfarin+aspirin 음성 대조 회귀 테스트 추가
- ETL 적재 스크립트 의사코드 완성도 향상

## 외부 API 호출 명세
이 슬라이스에서는 **외부 API 호출 0건**. 모두 로컬 CSV 적재 + 인덱스 룩업.

## DB 적재 — CSV 파일 → 테이블

### 파일 1: DUR 병용금기 (837,837행)
- **경로**: `data/_downloads/11983_ex/의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv`
- **인코딩**: cp949
- **테이블**: `ref.dur_combo_contraindications`

**CSV → DB 매핑**:

| CSV 컬럼 | DB 컬럼 | 변환 |
|---|---|---|
| 성분명A | ingredient_a_raw | as-is |
| 성분코드A | gnl_nm_cd_a | as-is |
| 제품코드A | product_code_a | as-is |
| 제품명A | product_name_a | as-is |
| 업체명A | entp_name_a | as-is |
| 급여여부A | reimbursement_a | as-is |
| 성분명B~급여여부B | ingredient_b_*, ... | as-is (A와 동일 구조) |
| 고시번호 | gazette_no | as-is |
| 고시일자 | gazette_date | YYYY-MM-DD 파싱 |
| 상세정보 | reason_detail | as-is |
| 비고 | remarks | as-is |
| **(생성)** | **ingredient_a_norm** | `normalize_drug_name(ingredient_a_raw)` |
| **(생성)** | **ingredient_b_norm** | `normalize_drug_name(ingredient_b_raw)` |

```sql
CREATE TABLE ref.dur_combo_contraindications (
    id                  BIGSERIAL PRIMARY KEY,
    ingredient_a_raw    VARCHAR(300) NOT NULL,
    ingredient_a_norm   VARCHAR(200) NOT NULL,             -- ★ 매칭 키 (정확 매칭)
    gnl_nm_cd_a         VARCHAR(20),
    product_code_a      VARCHAR(20),
    product_name_a      VARCHAR(500),
    entp_name_a         VARCHAR(200),
    reimbursement_a     VARCHAR(20),
    ingredient_b_raw    VARCHAR(300) NOT NULL,
    ingredient_b_norm   VARCHAR(200) NOT NULL,             -- ★ 매칭 키
    gnl_nm_cd_b         VARCHAR(20),
    product_code_b      VARCHAR(20),
    product_name_b      VARCHAR(500),
    entp_name_b         VARCHAR(200),
    reimbursement_b     VARCHAR(20),
    gazette_no          VARCHAR(20),
    gazette_date        DATE,
    reason_detail       TEXT,
    remarks             TEXT,
    source_csv_date     VARCHAR(20) NOT NULL DEFAULT '2025.6',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ★ 정확 매칭용 B-tree 인덱스 (v1의 LIKE 패턴 대체)
CREATE INDEX idx_dur_combo_norm_a ON ref.dur_combo_contraindications(ingredient_a_norm);
CREATE INDEX idx_dur_combo_norm_b ON ref.dur_combo_contraindications(ingredient_b_norm);
CREATE INDEX idx_dur_combo_pair ON ref.dur_combo_contraindications(ingredient_a_norm, ingredient_b_norm);

-- Idempotency: 같은 (성분A,B,고시번호) 중복 적재 방지
CREATE UNIQUE INDEX uniq_dur_combo 
    ON ref.dur_combo_contraindications(gnl_nm_cd_a, gnl_nm_cd_b, gazette_no);
```

### 파일 2~3: 노인주의 (572 + 1,083행)
```sql
CREATE TABLE ref.dur_elderly_caution (
    id                  BIGSERIAL PRIMARY KEY,
    ingredient_raw      VARCHAR(300) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    gnl_nm_cd           VARCHAR(20),
    product_code        VARCHAR(20),
    product_name        VARCHAR(500),
    entp_name           VARCHAR(200),
    detail              TEXT,                              -- 약품상세정보
    gazette_no          VARCHAR(20),
    gazette_date        DATE,
    reimbursement       VARCHAR(20),
    source_csv_date     VARCHAR(20) NOT NULL DEFAULT '2025.6',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(gnl_nm_cd, gazette_no)
);
CREATE INDEX idx_dur_elderly_norm ON ref.dur_elderly_caution(ingredient_norm);

CREATE TABLE ref.dur_elderly_nsaid_caution (LIKE ref.dur_elderly_caution INCLUDING ALL);
```

### 파일 4·5: 연령금기·임부금기 (Slice 05에서 적재)
이 슬라이스에서는 테이블만 생성, 적재는 Slice 05.

```sql
CREATE TABLE ref.dur_age_contraindication (
    id                  BIGSERIAL PRIMARY KEY,
    ingredient_raw      VARCHAR(300) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    gnl_nm_cd           VARCHAR(20),
    age_threshold       INT,
    age_unit            VARCHAR(20),
    condition           VARCHAR(20),                       -- '미만' | '이하'
    detail              TEXT,
    gazette_date        DATE,
    source_csv_date     VARCHAR(20) NOT NULL DEFAULT '2025.6',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(gnl_nm_cd, age_threshold, condition)
);
CREATE INDEX idx_dur_age_norm ON ref.dur_age_contraindication(ingredient_norm);

CREATE TABLE ref.dur_pregnancy_contraindication (
    id                  BIGSERIAL PRIMARY KEY,
    ingredient_raw      VARCHAR(300) NOT NULL,
    ingredient_norm     VARCHAR(200) NOT NULL,
    grade               INT,
    detail              TEXT,
    gazette_date        DATE,
    source_csv_date     VARCHAR(20) NOT NULL DEFAULT '2025.6',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(gnl_nm_cd, grade)
);
CREATE INDEX idx_dur_preg_norm ON ref.dur_pregnancy_contraindication(ingredient_norm);
```

## ETL 스크립트

```python
import csv
from pathlib import Path

BATCH_SIZE = 1000

def load_dur_combo_csv(csv_path: Path):
    with open(csv_path, encoding='cp949', newline='') as f:
        reader = csv.DictReader(f)
        batch = []
        for row in reader:
            batch.append({
                'ingredient_a_raw': row['성분명A'],
                'ingredient_a_norm': normalize_drug_name(row['성분명A']),
                'gnl_nm_cd_a': row['성분코드A'],
                'product_code_a': row['제품코드A'],
                'product_name_a': row['제품명A'],
                'entp_name_a': row['업체명A'],
                'reimbursement_a': row['급여여부A'],
                'ingredient_b_raw': row['성분명B'],
                'ingredient_b_norm': normalize_drug_name(row['성분명B']),
                'gnl_nm_cd_b': row['성분코드B'],
                'product_code_b': row['제품코드B'],
                'product_name_b': row['제품명B'],
                'entp_name_b': row['업체명B'],
                'reimbursement_b': row['급여여부B'],
                'gazette_no': row['고시번호'],
                'gazette_date': parse_date(row['고시일자']),
                'reason_detail': row['상세정보'],
                'remarks': row['비고'],
            })
            if len(batch) >= BATCH_SIZE:
                db.bulk_insert_or_ignore("ref.dur_combo_contraindications", batch)  
                # idempotent via UNIQUE
                batch = []
        if batch:
            db.bulk_insert_or_ignore("ref.dur_combo_contraindications", batch)
```

## 매칭 로직 (DURRuleEngine — PRD 모듈 ②)

⚠️ **v1→v2 핵심 변경**: `LIKE '%X%'` 폐기 → `=` 정확 매칭으로 인덱스 활용.

```python
def check_combination(drug_a: DrugIdentity, drug_b: DrugIdentity) -> list[DurViolation]:
    """
    정규화된 base name으로 정확 매칭 (인덱스 활용).
    
    drug_a.main_ingr_norm = "amlodipine" (slice 01에서 정규화돼 저장)
    drug_b.main_ingr_norm = "simvastatin"
    """
    norm_a = drug_a.main_ingr_norm
    norm_b = drug_b.main_ingr_norm
    
    # 양방향 정확 매칭 — idx_dur_combo_pair 활용
    rows = db.execute("""
        SELECT id, ingredient_a_raw, ingredient_b_raw, reason_detail, gazette_no, gazette_date
        FROM ref.dur_combo_contraindications
        WHERE (ingredient_a_norm = :a AND ingredient_b_norm = :b)
           OR (ingredient_a_norm = :b AND ingredient_b_norm = :a)
    """, {"a": norm_a, "b": norm_b})
    
    # dedupe: (성분A,성분B) 쌍 단위
    seen_pairs = set()
    violations = []
    for row in rows:
        pair = tuple(sorted([row.ingredient_a_raw, row.ingredient_b_raw]))
        if pair in seen_pairs:
            continue
        seen_pairs.add(pair)
        violations.append(DurViolation(
            type="병용금기",
            ingredient_a=row.ingredient_a_raw,
            ingredient_b=row.ingredient_b_raw,
            reason=row.reason_detail,
            gazette_no=row.gazette_no,
            gazette_date=row.gazette_date,
        ))
    return violations


def check_elderly(drug: DrugIdentity, patient_age: int) -> list[DurViolation]:
    if patient_age < 65:
        return []
    rows = db.execute("""
        SELECT * FROM ref.dur_elderly_caution 
        WHERE ingredient_norm = :n
        UNION ALL
        SELECT * FROM ref.dur_elderly_nsaid_caution 
        WHERE ingredient_norm = :n
    """, {"n": drug.main_ingr_norm})
    return [DurViolation(type="노인주의", ingredient_a=row.ingredient_raw, reason=row.detail) for row in rows]
```

## SafetyJudge MVP (PRD 모듈 ④ — 1단계)

```python
def judge_mvp(parent: PatientContext, current_drugs: list[DrugIdentity], 
              new_drug: DrugIdentity) -> SafetyVerdict:
    evidences = []
    
    for existing in current_drugs:
        for v in check_combination(new_drug, existing):
            evidences.append(Evidence(source="DUR", risk_level="병용금기", **v.dict()))
    
    if parent.age >= 65:
        for v in check_elderly(new_drug, parent.age):
            evidences.append(Evidence(source="DUR", risk_level="노인주의", **v.dict()))
    
    # 통합 판정
    if any(e.risk_level == "병용금기" for e in evidences):
        decision = "BLOCK"
    elif any(e.risk_level == "노인주의" for e in evidences):
        decision = "WARN"
    else:
        decision = "ALLOW"
    
    return SafetyVerdict(decision=decision, evidences=evidences)
```

## API 계약 (이 슬라이스가 노출)

```
POST /v1/safety/check
  Request:
  {
    "parent_id": "uuid",
    "age": 72,
    "current_drugs": ["202106092", "200610660"],
    "new_drug": "200502107"
  }
  
  Response 200 (ALLOW/WARN/INFO):
  {
    "decision": "WARN",
    "evidences": [
      {
        "source": "DUR",
        "type": "병용금기" | "노인주의",
        "ingredient_a": "...",
        "ingredient_b": "...",
        "reason": "...",
        "gazette_no": "...",
        "gazette_date": "..."
      }
    ]
  }
  
  Response 409 (BLOCK — v6 결정으로 HTTP 409 통일):
  {
    "error": "block",
    "verdict": { ... 위와 동일 ... }
  }
```

## End-to-End 흐름 (의사코드)

```
사용자 액션: 자녀가 부모님 약장에 새 약 추가
  ↓
1. POST /v1/safety/check { parent_id, age=72, current_drugs=[...], new_drug=ITEM_SEQ }
  ↓
2. 각 ITEM_SEQ를 ref.drugs_master에서 조회 → main_ingr_norm 획득
  ↓
3. for existing in current_drugs:
     check_combination(new_drug, existing)
     → SELECT ... WHERE ingredient_a_norm = :norm_a AND ingredient_b_norm = :norm_b
     → 또는 반대 방향
  ↓
4. if age ≥ 65: check_elderly(new_drug, 72)
  ↓
5. 통합 판정 → BLOCK/WARN/ALLOW
  ↓
6. BLOCK → 409, 나머지 → 200
```

## 수락 기준

- [ ] `ref.dur_combo_contraindications` **837,837행** 적재 + 인덱스 + UNIQUE 제약
- [ ] `ref.dur_elderly_caution` 572행, `ref.dur_elderly_nsaid_caution` 1,083행 적재
- [ ] `ref.dur_age_contraindication`·`ref.dur_pregnancy_contraindication` 테이블 생성 (적재는 Slice 05)
- [ ] **양성 대조**: amlodipine + itraconazole → BLOCK (5,520건 매칭 → dedupe 후 1개 evidence)
- [ ] **양성 대조**: 5-fluorouracil + tegafur → BLOCK
- [ ] **음성 대조 (한국 DUR 한계 확인)**: amlodipine + simvastatin → ALLOW (DUR엔 없음, slice 03에서 회수 예정)
- [ ] **음성 대조**: warfarin + aspirin → ALLOW (한국 DUR엔 없음)
- [ ] 노인(72세) + 노인주의 약물 → WARN
- [ ] 응답 시간 **< 50ms** (정확 매칭 인덱스 사용 검증, EXPLAIN으로 인덱스 hit 확인)
- [ ] dedupe: 같은 (성분A, 성분B) 짝이 중복으로 evidence에 안 나옴
- [ ] BLOCK 응답이 **HTTP 409**로 통일

## 회귀 자산
- DUR CSV 5개 (`data/_downloads/11983_ex/*.csv`)
- 양성/음성 대조 테스트 케이스 fixture

## 환경변수
- 없음 (외부 API 호출 없음)

## 범위 밖
- NB AI 추출 (Slice 03)
- 환자분류 금기 (Slice 05)
- 임부금기·연령금기 데이터 적재 (Slice 05)
- 알람 푸시 (별도 PRD)

## Smoke Test
```bash
# 적재 확인
psql $DATABASE_URL -c "SELECT count(*) FROM ref.dur_combo_contraindications;"  # 837837

# 양성 대조
curl -X POST http://localhost:8000/v1/safety/check \
  -H "Content-Type: application/json" \
  -d '{"parent_id":"t","age":72,"current_drugs":["<amlodipine ITEM_SEQ>"],"new_drug":"<itraconazole ITEM_SEQ>"}'
# 기대: HTTP 409 + decision="BLOCK" + evidences에 amlodipine·itraconazole

# 음성 대조 (slice 03에서 회수)
curl -X POST http://localhost:8000/v1/safety/check \
  -d '{"parent_id":"t","age":72,"current_drugs":["<amlodipine>"],"new_drug":"<simvastatin>"}'
# 기대: HTTP 200 + decision="ALLOW" (slice 03 통합 후 WARN으로 회수)
```
