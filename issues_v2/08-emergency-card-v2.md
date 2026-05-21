# Slice 08 v2 — 응급카드 (알레르기 + 항응고제 정렬)

## Why (사용자 가치)
부모님이 응급 상황(쓰러짐·의식 없음)에 처했을 때, 응급의료진이 QR 스캔으로 1분 안에:
- **알레르기** (TOP 우선 — 약 처치 결정)
- **항응고제 복용** (수술·시술 결정)
- 기저질환·복용 약 전체·다니는 병원·단골 약국·자녀 연락처

PRD v6의 "Step 6. 응급카드" 복원 + 모듈 ⑨ 신설. 응급실 약 파악 평균 15분 → 1분 단축.

## 의존성 (★ v1→v2 추가)
- Slice 00 v2: 4-schema
- Slice 04 v2: `app.patient_profiles`, `patient_medications`, **`patient_allergies` (v2 신규)**, `device_tokens`
- **Slice 05 v2**: `app.patient_conditions` (snapshot에 활용) ★ v1엔 누락

## v1 → v2 변경
- DDL에 schema prefix
- **`app.patient_allergies` 활용** (v1엔 데이터 없었음 — 정렬 동작 불가였음)
- **항응고제 정렬 로직**: `drugs_master.atc_code LIKE 'B01A%'` 우선
- 의존성 05 추가 (응급카드 snapshot에 conditions 활용)
- access_count 원자적 증가 (UPDATE atomic increment)
- token 회전 정책 명시
- rate limit 구체 수치

## v2 → v2.1 변경 (cross-ref 검수 반영)
- `calc_age()` → **`app.calc_age()`** (schema-qualified, slice 04 정의와 정합)

## 외부 API 호출 명세

### API 1 — HIRA 병원정보서비스
- **sno**: 11999
- **endpoint**: `http://apis.data.go.kr/B551182/hospInfoServicev2/getHospBasisList`

| 파라미터 | 필수 | 값 출처 | 예시 |
|---|---|---|---|
| `serviceKey` | ✅ | env.HIRA_API_KEY | f204... |
| `pageNo` | - | 1 | 1 |
| `numOfRows` | - | 10 | 10 |
| `yadmNm` | - | 자녀 입력 병원명 | "경북대학교병원" |
| `sidoCd`/`sgguCd` | - | 부모 주소 변환 | 230000/230010 |

**응답 (XML)**:
```xml
<item>
  <ykiho>JDQ4MTYwMSM1MSMkMSMkOCMkMyM=</ykiho>
  <yadmNm>경북대학교병원</yadmNm>
  <clCdNm>상급종합</clCdNm>
  <addr>대구광역시 중구 ...</addr>
  <telno>053-...</telno>
  <xPos>128.6010</xPos>
  <yPos>35.8567</yPos>
</item>
```

| 필드 | 사용처 |
|---|---|
| `ykiho` | ★ `app.parent_hospitals.ykiho` + API 2 입력 |
| `yadmNm` | `app.parent_hospitals.yadm_nm` |
| `clCdNm`·`addr`·`telno`·`xPos`·`yPos` | 동일 컬럼 |

### API 2 — HIRA 의료기관별상세정보서비스
- **sno**: 12101
- **endpoint**: `http://apis.data.go.kr/B551182/MadmDtlInfoService2.7/getDtlInfo2.7`

| 파라미터 | 필수 | 값 출처 |
|---|---|---|
| `serviceKey` | ✅ | env.HIRA_API_KEY |
| `ykiho` | ✅ | API 1.ykiho |

**응답 (XML)**:
```xml
<item>
  <emyNgtTelNo1>053-...</emyNgtTelNo1>
  <emyNgtYn>Y</emyNgtYn>
  <emyDayTelNo1>053-...</emyDayTelNo1>
  <emyDayYn>Y</emyDayYn>
  <trmtMonStart>0900</trmtMonStart>
  <trmtMonEnd>1800</trmtMonEnd>
  ...
</item>
```

### API 3 — HIRA 약국정보서비스
- **sno**: 12100
- **endpoint**: `http://apis.data.go.kr/B551182/pharmacyInfoService/getParmacyBasisList`

| 파라미터 | 필수 | 값 출처 |
|---|---|---|
| `serviceKey` | ✅ | env.HIRA_API_KEY |
| `sidoCd`·`sgguCd` 또는 `xPos`·`yPos`·`radius` | - | 부모 주소·좌표 |

## DB 적재

### Table: `app.parent_hospitals`

```sql
CREATE TABLE app.parent_hospitals (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    ykiho           VARCHAR(500) NOT NULL,
    yadm_nm         VARCHAR(200) NOT NULL,
    cl_cd_nm        VARCHAR(50),
    addr            VARCHAR(500),
    telno           VARCHAR(50),
    x_pos           NUMERIC(12,6),
    y_pos           NUMERIC(12,6),
    is_regular      BOOLEAN NOT NULL DEFAULT FALSE,
    last_visited    DATE,
    added_by        VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_parent_hosp ON app.parent_hospitals(parent_id);
CREATE UNIQUE INDEX uniq_parent_ykiho ON app.parent_hospitals(parent_id, ykiho);
```

### Table: `app.hospital_emergency_info`

```sql
CREATE TABLE app.hospital_emergency_info (
    ykiho                   VARCHAR(500) PRIMARY KEY,
    yadm_nm                 VARCHAR(200),
    night_er_available      CHAR(1),
    night_er_phone_1        VARCHAR(50),
    night_er_phone_2        VARCHAR(50),
    day_er_available        CHAR(1),
    day_er_phone_1          VARCHAR(50),
    day_er_phone_2          VARCHAR(50),
    weekly_hours            JSONB,
    sun_closed              VARCHAR(100),
    holi_closed             VARCHAR(100),
    refreshed_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Table: `app.parent_pharmacies`

```sql
CREATE TABLE app.parent_pharmacies (
    id              BIGSERIAL PRIMARY KEY,
    parent_id       UUID NOT NULL REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    ykiho           VARCHAR(500) NOT NULL,
    yadm_nm         VARCHAR(200) NOT NULL,
    addr            VARCHAR(500),
    telno           VARCHAR(50),
    x_pos           NUMERIC(12,6),
    y_pos           NUMERIC(12,6),
    is_regular      BOOLEAN NOT NULL DEFAULT FALSE,
    visit_count     INT NOT NULL DEFAULT 0,
    last_visited    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(parent_id, ykiho)
);
CREATE INDEX idx_parent_pharm ON app.parent_pharmacies(parent_id);
```

### Table: `app.emergency_cards`

```sql
CREATE TABLE app.emergency_cards (
    parent_id           UUID PRIMARY KEY REFERENCES app.patient_profiles(parent_id) ON DELETE CASCADE,
    qr_token            VARCHAR(64) NOT NULL UNIQUE,
    snapshot            JSONB NOT NULL,
    snapshot_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until         TIMESTAMPTZ NOT NULL,
    access_count        INT NOT NULL DEFAULT 0,
    last_accessed_at    TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ
);
CREATE INDEX idx_em_card_token ON app.emergency_cards(qr_token) WHERE revoked_at IS NULL;
```

## 흐름 (의사코드)

### 응급카드 생성·갱신

```python
def regenerate_emergency_card(parent_id: UUID) -> EmergencyCard:
    parent = db.fetch_one("app.patient_profiles", parent_id=parent_id)
    
    # 1) 부모 약장 — drugs_master JOIN으로 정보 보강
    meds = db.execute("""
        SELECT pm.added_at, pm.source,
               dm.item_seq, dm.item_name, dm.main_ingr_en, dm.atc_code,
               dm.specialty_type
        FROM app.patient_medications pm
        JOIN ref.drugs_master dm ON pm.item_seq = dm.item_seq
        WHERE pm.parent_id = :pid AND pm.deleted_at IS NULL
    """, {"pid": parent_id}).all()
    
    # 2) 기저질환 (slice 05 — disease_master JOIN)
    conditions = db.execute("""
        SELECT pc.disease_code, dm.sick_nm
        FROM app.patient_conditions pc
        JOIN ref.disease_master dm ON pc.disease_code = dm.sick_cd
        WHERE pc.parent_id = :pid AND pc.deleted_at IS NULL
    """, {"pid": parent_id}).all()
    
    # 3) 알레르기 (slice 04)
    allergies = db.execute("""
        SELECT allergen_type, allergen_name, severity, notes
        FROM app.patient_allergies
        WHERE parent_id = :pid AND deleted_at IS NULL
        ORDER BY severity = 'severe' DESC, severity = 'moderate' DESC
    """, {"pid": parent_id}).all()
    
    # 4) 병원·응급실
    hospitals = db.execute("""
        SELECT ph.*, hei.night_er_phone_1, hei.day_er_phone_1, hei.weekly_hours
        FROM app.parent_hospitals ph
        LEFT JOIN app.hospital_emergency_info hei ON ph.ykiho = hei.ykiho
        WHERE ph.parent_id = :pid
        ORDER BY ph.is_regular DESC, ph.last_visited DESC
    """, {"pid": parent_id}).all()
    
    # 5) 약국
    pharmacies = db.execute("""
        SELECT * FROM app.parent_pharmacies
        WHERE parent_id = :pid
        ORDER BY is_regular DESC, visit_count DESC
        LIMIT 3
    """, {"pid": parent_id}).all()
    
    # 6) ★ v2: 항응고제 우선 정렬
    # ATC B01A* (항혈전제) 우선
    is_anticoagulant = lambda m: m.atc_code and m.atc_code.startswith("B01A")
    meds_sorted = sorted(meds, key=lambda m: (
        not is_anticoagulant(m),    # 항응고제 TRUE → 최상단
        -m.added_at.timestamp()      # 최근 추가 순
    ))
    
    # 7) snapshot 구성 — 우선순위 명시
    snapshot = {
        "patient": {
            "name": parent.display_name,
            "age": app.calc_age(parent.birthdate),    # ★ v2.1: schema-qualified (04에 정의)
            "sex": parent.sex,
            "address": f"{parent.address_sido} {parent.address_sigungu or ''}",
        },
        # ★ TOP 1순위 — 알레르기
        "allergies": [
            {
                "type": a.allergen_type,
                "name": a.allergen_name,
                "severity": a.severity,
                "notes": a.notes,
            } for a in allergies
        ],
        # ★ TOP 2순위 — 항응고제 표시
        "critical_drugs": [
            {
                "item_name": m.item_name,
                "main_ingr_en": m.main_ingr_en,
                "atc_code": m.atc_code,
                "warning": "ANTICOAGULANT - 수술·시술 시 출혈 위험"
            } for m in meds_sorted if is_anticoagulant(m)
        ],
        # 기저질환
        "conditions": [
            { "code": c.disease_code, "name": c.sick_nm }
            for c in conditions
        ],
        # 전체 약장 (항응고제 위, 그 뒤 최신순)
        "medications": [
            {
                "item_seq": m.item_seq,
                "item_name": m.item_name,
                "main_ingr_en": m.main_ingr_en,
                "atc_code": m.atc_code,
                "is_anticoagulant": is_anticoagulant(m),
                "added_at": m.added_at.isoformat(),
            } for m in meds_sorted
        ],
        # 병원
        "hospitals": [h.to_dict() for h in hospitals],
        # 약국
        "pharmacies": [p.to_dict() for p in pharmacies],
        # 자녀 연락처 (별도 가족 연결 PRD — MVP는 빈 배열 또는 placeholder)
        "emergency_contacts": [],
    }
    
    # 8) QR 토큰 (URL-safe, 64자 정도)
    qr_token = secrets.token_urlsafe(48)
    
    # 9) upsert
    db.upsert("app.emergency_cards",
        unique='parent_id',
        parent_id=parent_id,
        qr_token=qr_token,
        snapshot=snapshot,
        snapshot_at=now(),
        valid_until=now() + timedelta(days=90),
        access_count=0,
        revoked_at=None
    )
    
    return em_card
```

### 외부 QR 토큰 → snapshot 노출

```python
def get_emergency_card_by_token(token: str) -> dict:
    em = db.execute("""
        SELECT * FROM app.emergency_cards
        WHERE qr_token = :t AND revoked_at IS NULL
    """, {"t": token}).first()
    if not em:
        raise NotFound("emergency_card_not_found")
    if em.valid_until < now():
        raise Expired("emergency_card_expired")
    
    # ★ v2: 원자적 증가 (read-modify-write 제거)
    db.execute("""
        UPDATE app.emergency_cards
        SET access_count = access_count + 1, last_accessed_at = NOW()
        WHERE qr_token = :t
    """, {"t": token})
    
    return em.snapshot
```

### 토큰 회전 정책 (★ v2)

| 트리거 | 동작 |
|---|---|
| 자녀 명시 요청 (`POST /regenerate`) | 새 qr_token 발급, 기존 토큰 즉시 무효 |
| 부모 약장·기저질환·알레르기 변경 | snapshot 갱신만, **qr_token 유지** (90일 만료 시까지) |
| 90일 만료 | 자동 만료 (`valid_until < now()`) — 자녀 앱이 재발급 안내 |
| 의심스러운 접근 패턴 (rate limit 초과) | 자동 revoke + 자녀 알람 |

### Rate Limit

```yaml
GET /em/{token}:
  - IP당 분당 10회
  - 토큰당 시간당 60회
  - 토큰당 일일 200회 (초과 시 자동 revoke + 알람)
```

## API 계약

```
POST /v1/parents/{parent_id}/hospitals
  Request: { "yadm_nm": "경북대학교병원" } 또는 { "ykiho": "..." }
  Response 201: ParentHospital + 응급실 정보

GET /v1/parents/{parent_id}/hospitals
POST /v1/parents/{parent_id}/pharmacies
GET /v1/parents/{parent_id}/pharmacies

POST /v1/parents/{parent_id}/emergency-card/regenerate
  Response 200:
  {
    "qr_token": "url-safe-string",
    "qr_url": "https://emergency.엄마약.com/c/{token}",
    "valid_until": "2026-08-18T...",
    "snapshot_at": "..."
  }

POST /v1/parents/{parent_id}/emergency-card/revoke
  Response 204

GET /em/{qr_token}                                  ← 외부 응급의료진용 (인증 없음)
  Response 200: snapshot
  Headers: Cache-Control: no-store, X-RateLimit-*
  Rate-limited (위 정책)
```

## 수락 기준

- [ ] 4개 테이블 + 인덱스 + UNIQUE + 트리거 생성
- [ ] HIRA 3개 API 활용신청 + 첫 호출 성공
- [ ] 응급카드 생성 → snapshot에 다음 모두 포함:
  - allergies (severity 정렬)
  - critical_drugs (B01A* 항응고제)
  - medications (항응고제 최상단)
  - conditions (slice 05 JOIN)
- [ ] **항응고제 정렬**: 부모가 wafarin 등 ATC `B01AA*` 약 복용 시 medications 배열 최상단 + critical_drugs에 별도 표시
- [ ] QR URL 외부 접근 → snapshot JSON
- [ ] access_count 원자적 증가 (동시 접근 부하 테스트 통과)
- [ ] 토큰 만료 후 → 410 Gone
- [ ] 자녀 revoke → 즉시 410
- [ ] Rate limit 시 → 429 + 자동 revoke 트리거
- [ ] 약장 변경 후 응급카드 자동 갱신 (snapshot_at 갱신, qr_token 유지)

## 회귀 자산
- 없음 (실호출 fixture로 자체 생성)

## 환경변수
- `HIRA_API_KEY`

## 범위 밖
- **자녀 연락처 관리** — 별도 가족 연결 PRD
- **QR 이미지 생성** — 프론트엔드
- **응급의료진 전용 앱·UI**
- **GDPR/개인정보보호 법무 검토** (운영 전 별도)
- **응급카드 인쇄 PDF**
- **부모 본인이 응급카드 표시 — 음성 호출** (Phase 2)

## Smoke Test
```bash
# 1) 단골 병원 등록
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/hospitals" \
  -d '{"yadm_nm":"경북대학교병원"}'

# 2) 응급카드 생성
curl -X POST "http://localhost:8000/v1/parents/${PARENT_ID}/emergency-card/regenerate" | jq .

# 3) 외부 QR 조회 시뮬
TOKEN=$(curl ... | jq -r '.qr_token')
curl "http://localhost:8000/em/${TOKEN}" | jq .

# 4) 항응고제 시뮬: warfarin 추가 후 응급카드 재생성 → snapshot.critical_drugs에 표시
curl "http://localhost:8000/em/${TOKEN}" | jq '.critical_drugs'
```

---

## v2 슬라이스 작성 완료 종합

이 슬라이스(08)를 끝으로 9개 슬라이스 v2 + README v2 작성 완료. 모든 v1 검수 블로커 12개가 v6 설계 결정으로 해결됨:

| 블로커 | 해결 슬라이스 |
|---|---|
| patient.conditions 모호 | 05 (정규화 테이블 확정) |
| child_device_token 없음 | 04 (device_tokens 신규) |
| patient_allergies 없음 | 04 (신규) + 08 (활용) |
| DUR LIKE 인덱스 무력화 | 02 (정확 매칭) |
| README 의존성 그래프 부정확 | README + 각 슬라이스 헤더 |
| 04 deps에 03 누락 | 04 (deps 추가) |
| nb_interactions ALTER 분산 | 03 (entry_type 초기 통합) |
| WeatherAdvisory.rule_id 누락 | 06 (필드 추가) |
| 04 BLOCK 응답 모호 | 04 (HTTP 409 통일) |
| 08 정렬 동작 불가 | 04 (allergies) + 08 (항응고제 ATC) |
| PRD에 Step 6 부재 | PRD v6 (복원) + 08 (참조) |
| 환경변수 정확 이름 | 00 (.env.example 7개 명시) |
