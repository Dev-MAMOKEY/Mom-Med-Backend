# Slice 04 v2 — 부모 등록 + 약장 + 알레르기 + 디바이스 토큰

## Why (사용자 가치)
자녀가 어머니를 시스템에 **등록**하고 약장·알레르기·자녀 디바이스 토큰을 관리. 이후 슬라이스 05·07·08이 모두 이 컨텍스트를 활용.

## 의존성 (★ v1→v2 추가)
- Slice 00 v2: 4-schema, audit 트리거
- Slice 01 v2: `ref.drugs_master` (약 추가 시 식별)
- **Slice 02 v2**: `SafetyJudge.judge_mvp` (약 추가 시 안전 검사)  ★ v1엔 누락
- **Slice 03 v2**: `SafetyJudge.judge_full` (NB 통합)  ★ v1엔 누락

## v1 → v2 변경
- DDL에 schema prefix (`app.*`)
- ★ **`app.patient_allergies` 신규 테이블** (응급카드 1순위)
- ★ **`app.device_tokens` 신규 테이블** (slice 07 푸시용)
- 의존성에 02·03 명시 (safety_check 호출하므로)
- HTTP 409 통일 (BLOCK 시)
- birthdate → age 헬퍼 명시
- partial UNIQUE 인덱스 (soft delete 친화)

## 외부 API 호출 명세
이 슬라이스에서는 **외부 API 호출 0건**. CRUD + 내부 모듈 호출.

(약 추가 시 Slice 01의 `identify_drug_by_item_seq()` 내부 호출 — 새 약이면 캐시 워밍)

## DB 적재

### Table: `app.patient_profiles`

```sql
CREATE TABLE app.patient_profiles (
    parent_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name        VARCHAR(50) NOT NULL,              -- "어머니"
    birthdate           DATE NOT NULL,                     -- 만 나이 계산
    sex                 CHAR(1) NOT NULL CHECK (sex IN ('M','F')),
    address_sido        VARCHAR(50),
    address_sigungu     VARCHAR(50),
    address_dong        VARCHAR(50),
    nx                  SMALLINT,                          -- 기상청 격자 X (slice 06에서 채움)
    ny                  SMALLINT,
    is_pregnant         BOOLEAN NOT NULL DEFAULT FALSE,
    consent_data_share  BOOLEAN NOT NULL DEFAULT FALSE,
    consent_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_patient_address ON app.patient_profiles(address_sido, address_sigungu);
CREATE TRIGGER trg_patient_profiles_updated_at BEFORE UPDATE ON app.patient_profiles
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- 만 나이 계산 헬퍼 (앱 또는 view)
CREATE OR REPLACE FUNCTION app.calc_age(birthdate DATE) RETURNS INT AS $$
    SELECT EXTRACT(YEAR FROM AGE(birthdate))::INT;
$$ LANGUAGE SQL IMMUTABLE;
```

### Table: `app.patient_medications`

```sql
CREATE TABLE app.patient_medications (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    item_seq        VARCHAR(20) NOT NULL REFERENCES ref.drugs_master(item_seq),
    source          VARCHAR(20) NOT NULL CHECK (source IN ('prescription','otc','self_added')),
    added_by        VARCHAR(20) NOT NULL CHECK (added_by IN ('child','parent','system')),
    notes           TEXT,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ                            -- soft delete
);
CREATE INDEX idx_pmed_parent_active ON app.patient_medications(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_pmed_item ON app.patient_medications(item_seq);

-- 활성 동일 약 중복 방지 (partial UNIQUE — PostgreSQL 특화)
CREATE UNIQUE INDEX uniq_pmed_active_per_drug 
    ON app.patient_medications(parent_id, item_seq) 
    WHERE deleted_at IS NULL;
```

### ★ Table: `app.patient_allergies` (v2 신규)

```sql
CREATE TABLE app.patient_allergies (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    allergen_type   VARCHAR(20) NOT NULL CHECK (allergen_type IN ('drug','food','env','other')),
    allergen_name   VARCHAR(200) NOT NULL,                 -- 원본 텍스트 (예: "페니실린")
    allergen_norm   VARCHAR(200),                          -- 정규화 (약물만, normalize_drug_name)
    severity        VARCHAR(20) CHECK (severity IN ('severe','moderate','mild','unknown')),
    notes           TEXT,
    confirmed_at    DATE,                                  -- 진단 확인일
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_pall_parent_active ON app.patient_allergies(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_pall_allergen_norm ON app.patient_allergies(allergen_norm) WHERE allergen_norm IS NOT NULL;
```

### ★ Table: `app.device_tokens` (v2 신규 — slice 07 푸시용)

```sql
CREATE TABLE app.device_tokens (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    -- 자녀의 디바이스 (부모 약 관리하는 자녀가 푸시 받음)
    child_user_id   UUID,                                  -- 향후 자녀 계정 PRD에서 활성
    platform        VARCHAR(10) NOT NULL CHECK (platform IN ('fcm','apns','web')),
    
    -- ★ token은 pgcrypto로 컬럼 암호화 권장 (앱 레이어에서 pgp_sym_encrypt/decrypt)
    token_encrypted BYTEA NOT NULL,
    
    last_used_at    TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dtok_parent_active ON app.device_tokens(parent_id) WHERE revoked_at IS NULL;
```

### Table: `logs.safety_check_log` (파티션)

```sql
CREATE TABLE logs.safety_check_log (
    id                  BIGSERIAL,
    parent_id           UUID NOT NULL,
    new_drug_item_seq   VARCHAR(20) NOT NULL,
    decision            VARCHAR(10) NOT NULL CHECK (decision IN ('BLOCK','WARN','INFO','ALLOW')),
    evidence_summary    JSONB NOT NULL,
    checked_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (checked_at, id)
) PARTITION BY RANGE (checked_at);

-- 첫 파티션 (월별, pg_partman으로 자동 생성 권장)
CREATE TABLE logs.safety_check_log_2026_05 
    PARTITION OF logs.safety_check_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE INDEX idx_safety_log_parent ON logs.safety_check_log(parent_id, checked_at DESC);
```

## API 계약 (이 슬라이스가 노출)

### 부모 프로파일 CRUD

```
POST /v1/parents
  Request:
  {
    "display_name": "어머니",
    "birthdate": "1954-03-15",
    "sex": "F",
    "address_sido": "대구광역시",
    "address_sigungu": "중구",
    "consent_data_share": true
  }
  Response 201:
  {
    "parent_id": "uuid-...",
    "age": 72,
    "display_name": "어머니",
    ...
  }

GET /v1/parents/{parent_id}
  Response 200: profile + age + medication_count + condition_count + allergy_count
PATCH /v1/parents/{parent_id}
DELETE /v1/parents/{parent_id}  (soft delete or cascade)
```

### 약장 CRUD

```
GET /v1/parents/{parent_id}/medications
  Response 200:
  {
    "active": [
      {
        "id": 1,
        "item_seq": "200610660",
        "drug": { ... ref.drugs_master ... },
        "visual": { ... ref.pill_visuals ... },
        "source": "prescription",
        "added_at": "..."
      }
    ],
    "history": [ ... ]
  }

POST /v1/parents/{parent_id}/medications
  Request: { "item_seq": "200610660", "source": "prescription", "notes": "..." }
  
  내부 흐름:
    1. item_seq를 ref.drugs_master에서 조회 (없으면 slice 01 identify 호출)
    2. 현재 약장 + 부모 정보 조회 → SafetyJudge.judge_full() (slice 03)
    3. logs.safety_check_log에 기록
    4. decision에 따라 분기:
       - BLOCK → HTTP 409, 약장 추가 안 함
       - WARN/INFO/ALLOW → HTTP 201, 약장 추가
  
  Response 201 (WARN/INFO/ALLOW):
  {
    "id": 1,
    "item_seq": "...",
    "added_at": "...",
    "safety_check": { "decision": "WARN", "evidences": [...] }
  }
  
  Response 409 (BLOCK):
  {
    "error": "block",
    "verdict": { "decision": "BLOCK", "evidences": [...] }
  }

DELETE /v1/parents/{parent_id}/medications/{med_id}
  → deleted_at = NOW()  (soft delete)
  Response 204
```

### ★ 알레르기 CRUD (v2 신규)

```
POST /v1/parents/{parent_id}/allergies
  Request:
  {
    "allergen_type": "drug",
    "allergen_name": "페니실린",
    "severity": "severe"
  }
  Response 201: { id, allergen_norm, ... }

GET /v1/parents/{parent_id}/allergies
  Response 200: { "active": [...], "history": [...] }

DELETE /v1/parents/{parent_id}/allergies/{id}
```

### ★ 디바이스 토큰 (v2 신규)

```
POST /v1/parents/{parent_id}/device-tokens
  Request:
  {
    "platform": "fcm",
    "token": "fcm-token-string..."
  }
  → 서버에서 pgp_sym_encrypt로 암호화 후 저장
  Response 201: { id, platform, last_used_at: null }

DELETE /v1/parents/{parent_id}/device-tokens/{id}
  → revoked_at = NOW()
```

## 약 추가 시 자동 안전 검사 흐름 (의사코드)

```python
def add_medication(parent_id: UUID, item_seq: str, source: str) -> AddMedicationResult:
    # 1) 약 식별 (slice 01 캐시 우선)
    drug = identify_drug_by_item_seq(item_seq)  # ref.drugs_master 조회 or 식약처 API
    
    # 2) 부모 컨텍스트
    parent = db.fetch_one("app.patient_profiles", parent_id=parent_id)
    parent_ctx = PatientContext(
        age=app.calc_age(parent.birthdate),
        sex=parent.sex,
        is_pregnant=parent.is_pregnant,
        # conditions·allergies는 slice 05·이 슬라이스에서 채움
    )
    
    # 3) 현재 약장 (활성)
    current = db.execute("""
        SELECT pm.*, dm.* 
        FROM app.patient_medications pm
        JOIN ref.drugs_master dm ON pm.item_seq = dm.item_seq
        WHERE pm.parent_id = :pid AND pm.deleted_at IS NULL
    """, {"pid": parent_id})
    
    # 4) 안전 검사 (slice 02 + 03)
    verdict = safety_judge.judge_full(parent_ctx, current_drugs=current, new_drug=drug)
    
    # 5) 로그
    db.insert("logs.safety_check_log", parent_id=parent_id, 
              new_drug_item_seq=item_seq, decision=verdict.decision,
              evidence_summary=verdict.to_dict())
    
    # 6) BLOCK → HTTP 409
    if verdict.decision == "BLOCK":
        raise SafetyBlockError(verdict)  # → handler가 HTTP 409 응답
    
    # 7) 약장 추가
    med = db.insert("app.patient_medications", 
        parent_id=parent_id, item_seq=item_seq, source=source, added_by="child")
    
    return AddMedicationResult(medication=med, safety_check=verdict)
```

## End-to-End 사용자 시나리오

1. 자녀가 어머니 프로파일 등록:
   ```
   POST /v1/parents { "display_name":"어머니", "birthdate":"1954-03-15", ... }
   → parent_id = "uuid-abc"
   ```
2. 어머니 알레르기 등록:
   ```
   POST /v1/parents/uuid-abc/allergies { "allergen_type":"drug", "allergen_name":"페니실린", "severity":"severe" }
   ```
3. 자녀 디바이스 토큰 등록 (FCM):
   ```
   POST /v1/parents/uuid-abc/device-tokens { "platform":"fcm", "token":"..." }
   ```
4. 약장에 노바스크 추가:
   ```
   POST /v1/parents/uuid-abc/medications { "item_seq":"200610660", "source":"prescription" }
   → HTTP 201, safety_check.decision="ALLOW" (첫 약)
   ```
5. simvastatin 추가:
   ```
   POST /v1/parents/uuid-abc/medications { "item_seq":"<simvastatin>", "source":"prescription" }
   → HTTP 201, safety_check.decision="WARN" (NB로 amlodipine+simvastatin 회수)
   ```

## 수락 기준

- [ ] 4개 테이블 + 인덱스 + 트리거 + UNIQUE/CHECK 제약 생성
- [ ] `POST /v1/parents` → `parent_id` UUID 생성 + `age` 자동 계산
- [ ] **BLOCK 케이스 → HTTP 409** (v6 결정)
- [ ] WARN 케이스 → HTTP 201 + safety_check 포함
- [ ] `app.patient_medications`에 same (parent, item_seq, deleted_at IS NULL) 중복 추가 시 UNIQUE 위반
- [ ] soft delete: `DELETE` 후 `GET`의 active 배열에서 제외, history에 포함
- [ ] 알레르기 등록 → `allergen_norm`이 약물의 경우 정규화 저장
- [ ] 디바이스 토큰 등록 → DB에 plain token 노출 없음 (`pgp_sym_encrypt` 확인)
- [ ] `logs.safety_check_log` 모든 추가 시도 기록 (BLOCK도 포함)
- [ ] 동의 안 한 부모(`consent_data_share=false`) → 약장 GET 시 403

## 회귀 자산
없음 (CRUD 위주, slice 01·02·03의 회귀 자산 간접 활용).

## 환경변수
- `APP_ENCRYPTION_KEY` (pgcrypto 컬럼 암호화 마스터키)

## 범위 밖
- **가족 연결·자녀 본인인증** (별도 PRD)
- **실제 푸시 발송 인프라** (FCM/APNs 셋업은 별도 — 이 슬라이스는 토큰 저장만)
- **부모 본인 앱** (부모는 본인인증 1회만)
- **격자 좌표 자동 변환** (Slice 06에서 region_grid 활용 후 ALTER로 채움)
- **복약 순응도 분석** (Phase 2)

## Smoke Test
```bash
# 1) 부모 등록
PARENT_ID=$(curl -X POST http://localhost:8000/v1/parents \
  -H "Content-Type: application/json" \
  -d '{"display_name":"어머니","birthdate":"1954-03-15","sex":"F","address_sido":"대구광역시","address_sigungu":"중구","consent_data_share":true}' \
  | jq -r '.parent_id')

# 2) 알레르기 등록
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/allergies" \
  -d '{"allergen_type":"drug","allergen_name":"페니실린","severity":"severe"}'

# 3) 디바이스 토큰
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/device-tokens" \
  -d '{"platform":"fcm","token":"sample-fcm-token"}'

# 4) 약 추가 (안전 검사 자동)
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/medications" \
  -d '{"item_seq":"200610660","source":"prescription"}' | jq .

# 5) 토큰 암호화 확인 (plain text 없어야 함)
psql $DATABASE_URL -c "SELECT token_encrypted FROM app.device_tokens LIMIT 1;"
```
