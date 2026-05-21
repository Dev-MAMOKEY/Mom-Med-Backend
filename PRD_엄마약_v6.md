# PRD: 엄마약(Mom's Med) — 약물·시각·환경 통합 안전 모듈 v6

| 항목 | 내용 |
|---|---|
| 제품명 | 엄마약 (Mom's Med) |
| 버전 | v6 (2026-05-20, v5 동일 일자 후속 갱신) |
| v5 대비 변경 | **기술 스택·DB 스키마 아키텍처 잠금**(PostgreSQL 4-schema) + **Step 6 응급카드 섹션 복원** + **검수 발견 블로커 12개 설계 결정 반영** |
| 대상 독자 | 개발팀, 자문 의료진 |

---

## v5 → v6 변경 요약

| 영역 | v5 (이전) | v6 (현재) |
|---|---|---|
| DB 선택 | 암묵적 (PostgreSQL 가정) | **명시 잠금: PostgreSQL 14+ + pg_trgm + pgcrypto** |
| 스키마 구조 | 단일 schema, 테이블 16개 산재 | **4-schema 아키텍처: ref / app / derived / logs** |
| 응급카드 | 범위 밖 (별도 PRD) | **Step 6 섹션 복원, ⑨ EmergencyCardModule 추가** |
| 환자 기저질환 저장 | 모호 (JSONB or 테이블) | **정규화 테이블 `app.patient_conditions` 확정** |
| 알레르기 데이터 | 누락 | **`app.patient_allergies` 신규** (응급카드 1순위) |
| 푸시 토큰 저장 | 누락 | **`app.device_tokens` 신규** |
| NB 추출 entry_type | slice 03·05 분산 ALTER | **`derived.nb_interactions` 초기 정의에 통합** |
| 시계열 로그 | 단일 테이블 | **월별 RANGE 파티션** (logs.* 4개) |
| 민감 데이터 | 정책 미정 | **RLS + 컬럼 암호화 명시** |
| 검수 발견 블로커 | 12건 | **모두 설계 결정으로 해결** |

---

## 한 줄 요약 (v6)

자녀가 입력한 부모님 약 목록에 대해 (1) **약물 안전 검증**(DUR + NB 2겹), (2) **시각 식별 보조**(실 알약 사진), (3) **날씨 연계 주의 메시지**(8질병 × 4단계 = 32룰), (4) **응급카드**(병원·약국·알레르기·복약 정보 QR)를 통합 제공. PostgreSQL 4-schema 아키텍처. 데이터·룰셋 검증 완료, **블로커 해결 후 코드 구현 단계 진입**.

---

## 문제 정의 (Problem Statement)

v2~v5와 동일.

---

## 해결책 (Solution)

v5의 3개 모듈 + **응급카드 복원**:

### 1. 약물 안전 검증 (v5와 동일)
### 2. 시각 식별 보조 (v5와 동일)
### 3. 날씨 연계 주의 메시지 (v5와 동일, 32 룰 검증 완료)

### 4. ★ 응급카드 (v6에서 복원, 모듈 ⑨)

자녀의 부모님 정보(약장·기저질환·알레르기·다니는 병원·단골 약국)를 응급 의료진이 1분 안에 파악할 수 있도록 **QR 코드로 즉시 공유**.

**핵심 가치**: 응급실 의료진이 환자 약 파악에 평균 15분 → 1분 단축.

**구성**:
- 자녀 폰 + 부모 지갑에 QR
- 응급의료진 스캔 → snapshot JSON 즉시 노출 (인증 없음, 토큰 만료 정책)
- 우선순위 정렬: **알레르기 > 항응고제(ATC B01A*) > 기저질환 > 일반 복용약 > 보호자 연락처**

**데이터 소스**:
- 부모 약장: `app.patient_medications` (slice 04)
- 기저질환: `app.patient_conditions` (slice 05)
- 알레르기: `app.patient_allergies` (slice 04)
- 다니는 병원·응급실 연락처: HIRA 11999·12101 API
- 단골 약국: HIRA 12100 API

---

## 사용자 스토리

v5의 26개 + 응급카드 복원으로 응급실 의사 시나리오(스토리 13) 명시적 활성:

13. 응급실 의사로서, 환자가 의식이 없을 때 보호자 연락 없이도 복용 약·알레르기·항응고제 여부를 1분 안에 확인하고 싶다. 그래야 안전한 응급 처치를 할 수 있기 때문이다.

★ v6 신규 응급카드 관련 스토리:
27. 자녀로서, 어머니 약장에 변경이 생기면 응급카드가 자동 갱신되길 원한다. 그래야 항상 최신 정보가 응급실에 전달된다.
28. 자녀로서, QR 토큰이 외부에 노출됐을 때 즉시 재발급할 수 있길 원한다. 보안 사고 대비.

---

## 구현 결정 사항 (Implementation Decisions)

### 아키텍처 개요 (v6 갱신)

**8 운영 딥 모듈 + 1 빌드 타임 큐레이터**:

```
[입력: 약 / 처방전 / 기저질환 / 알레르기 / 오늘 날씨]
            │
            ▼
┌──────────────────────────────┐
│ ① DrugIdentityResolver       │  → drugs_master + 정규화
└──────────────────────────────┘
            │
   ┌────────┼─────────────┬──────────────┬─────────────────┐
   ▼        ▼             ▼              ▼                 ▼
┌────────┐┌────────┐┌────────────┐┌──────────────────┐┌─────────────┐
│ ② DUR ││ ③ NB  ││ ⑥ Pill    ││ ⑦ WeatherDisease │ │ ⑨ Emergency │
│ Engine ││Extract││  Visual    ││   Advisor        │ │   CardModule│
└────────┘└────────┘└────────────┘└──────────────────┘└─────────────┘
   │        │              │              │                 │
   └────┬───┘              │              │                 │
        ▼                  ▼              ▼                 ▼
┌─────────────┐    [알약 화면 표시]  [자녀 푸시]      [QR snapshot]
│④ SafetyJudge│
└─────────────┘
        │
        ▼
   [차단/경고/통과]

   ⑤ HallucinationVerifier  (③·⑧·검증 회귀 안전망)
   ⑧ RuleSetCurator  (빌드 타임 1회, 32룰 검증 완료)
```

**v5 대비 신규**:
- **⑨ EmergencyCardModule** 운영 모듈 추가 (slice 08)
- 슬라이스 매핑: ①→01, ②→02, ③→03+05, ④→02+03+05, ⑤→00, ⑥→01, ⑦→06+07, ⑧→build-time(seed), ⑨→08

### ★ 기술 스택 (v6 신규 — 명시 잠금)

| 영역 | 선택 | 이유 |
|---|---|---|
| **DB** | **PostgreSQL 14+** | JSONB·partial unique·pg_trgm 한글검색·RLS·ACID — 의료 데이터에 모두 필요 |
| **DB 확장** | `pg_trgm`, `pgcrypto`, `btree_gin`, `pg_stat_statements` | 한글 부분검색·UUID·복합 GIN·쿼리 분석 |
| **캐시** | **Redis 7+** | LLM 응답·API 응답·세션 캐싱 |
| **백엔드 언어** | Python 3.11+ (FastAPI) **또는** TypeScript (NestJS) | 팀 선호로 결정 — 둘 다 OK |
| **LLM** | Claude Sonnet 4 (1순위), GPT-4o (대체) | NB 추출·룰셋 큐레이션 검증된 모델 |
| **마이그레이션** | Alembic (Python) 또는 TypeORM Migration (TS) | 슬라이스 1개당 마이그레이션 1~2개 |
| **컨테이너** | Docker Compose (개발), k8s (운영) | 표준 |
| **CI** | GitHub Actions | 표준 |

### ★ DB 스키마 아키텍처 — 4 Schemas (v6 신규)

데이터 성격별로 4개 schema 분리:

```
mom_med database
├── ref      Reference Data    — 외부 마스터, 일·연 ETL, 백업 적게
├── app      Application Data  — 사용자 생성, OLTP, PITR 백업 필수
├── derived  Derived Data      — 시스템 가공 캐시, LLM 재호출로 복구
└── logs     Time-series Logs  — append-only, 월별 파티션
```

**왜 4-schema 분리**:
- 권한 분리 (운영 인력은 `ref.*` SELECT만)
- 백업 정책 분리 (`app.*`만 PITR, 다른 건 ETL 재실행으로 복구)
- 관찰성 (`logs.*` 별도 대시보드)
- 운영 단순화 (`pg_dump --schema=app`로 사용자 데이터만 백업)

### 테이블 목록 — 4-schema 매핑

#### `ref.*` (9개) — 외부 마스터

| 테이블 | PK | 출처 | 슬라이스 |
|---|---|---|---|
| `ref.drugs_master` | `item_seq` | 식약처 15095677 | 01 |
| `ref.pill_visuals` | `item_seq` (FK) | 식약처 15057639 | 01 |
| `ref.disease_master` | `sick_cd` | HIRA 11984 CSV | 05 |
| `ref.region_grid` | `id` | 기상청 xlsx (정적) | 06 |
| `ref.dur_combo_contraindications` | `id` | HIRA 11983 CSV | 02 |
| `ref.dur_elderly_caution` | `id` | HIRA 11983 CSV | 02 |
| `ref.dur_age_contraindication` | `id` | HIRA 11983 CSV | 02·05 |
| `ref.dur_pregnancy_contraindication` | `id` | HIRA 11983 CSV | 02·05 |
| `ref.weather_rules` | `id` | seed/weather_rules_v0.2.json | 06 |

#### `app.*` (8개) — 사용자 데이터

| 테이블 | PK | 슬라이스 | v6 신규? |
|---|---|---|---|
| `app.patient_profiles` | `parent_id` (UUID) | 04 | |
| `app.patient_conditions` | `id` | 05 | ★ 정규화 확정 |
| `app.patient_medications` | `id` | 04 | |
| `app.patient_allergies` | `id` | 04 | ★ 신규 |
| `app.device_tokens` | `id` | 04 | ★ 신규 |
| `app.parent_hospitals` | `id` | 08 | |
| `app.parent_pharmacies` | `id` | 08 | |
| `app.emergency_cards` | `parent_id` (FK 1:1) | 08 | |

#### `derived.*` (2개) — 가공·캐시

| 테이블 | PK | 슬라이스 | 비고 |
|---|---|---|---|
| `derived.nb_extractions` | `id` | 03 | LLM 재호출로 복구 가능 |
| `derived.nb_interactions` | `id` | 03·05 | ★ `entry_type` 초기 정의에 통합 |

#### `logs.*` (4개) — 시계열 파티션

| 테이블 | 파티션 키 | 슬라이스 |
|---|---|---|
| `logs.safety_check_log` | `checked_at` 월별 | 04 |
| `logs.advisory_push_log` | `sent_at` 월별 | 07 |
| `logs.weather_observations_daily` | `observed_date` 월별 | 07 |
| `logs.api_call_log` ★ | `called_at` 월별 | 신규 (모니터링) |

추가로 `app.hospital_emergency_info`는 캐시 성격이라 `derived.*`로 옮길지 검토 (slice 08에서 결정).

### ★ 블로커 12개 → 설계 결정 매핑

| # | 블로커 | v6 결정 |
|---|---|---|
| 1 | patient.conditions 형식 모호 | **정규화 `app.patient_conditions` 테이블 확정** + slice 06/07/08은 JOIN으로 가져옴 |
| 2 | child_device_token 정의 없음 | **`app.device_tokens` 신규 테이블** |
| 3 | patient_allergies 정의 없음 | **`app.patient_allergies` 신규 테이블** |
| 4 | 질병 코드 모델링 부재(slice 04) | 위 1번과 함께 해결 + slice 04는 patient_profiles만, conditions는 slice 05 책임 |
| 5 | DUR `LIKE '%...%'` 인덱스 무력화 | **`ingredient_norm = ?` 정확 매칭** + slice 00 정규화 강화 (닫힌 염 목록 12개) |
| 6 | README 의존성 그래프 부정확 | **재작성**: 04→02·03, 05→02, 06→04·05, 07→04·05·06, 08→04·05 |
| 7 | 04 deps에 03 누락 | **04 deps에 02·03 추가** (or safety_check feature flag) — 정책: hard dep |
| 8 | 05의 ALTER 분산 | **derived.nb_interactions에 entry_type 초기 포함**, app.patient_profiles는 그대로 |
| 9 | WeatherAdvisory.rule_id 누락 | **WeatherAdvisory dataclass에 rule_id 추가** (slice 06) |
| 10 | 04 BLOCK 응답 코드 모호 | **HTTP 409로 통일**, body: `{"error":"block","verdict":{...}}` |
| 11 | 08 정렬 동작 불가(알레르기·항응고제) | **`app.patient_allergies` 추가** + `ref.drugs_master.is_anticoagulant_flag` 또는 `atc_code LIKE 'B01A%'` 기반 정렬 |
| 12 | PRD v5에 Step 6 부재 | **v6 본문에 Step 6 응급카드 섹션 명시 추가** (위 해결책 4번) |

### ★ PK 전략

| 케이스 | PK 타입 | 이유 |
|---|---|---|
| 외부 노출 (URL·QR) | UUID v4 (`gen_random_uuid()`) | 추측 불가, 순차 누출 없음. parent_id, emergency_cards.qr_token |
| 외부 식별자 안정 | 외부 ID 그대로 | item_seq, sick_cd — surrogate key 추가 시 join 비용↑ |
| 내부 전용 | BIGSERIAL | 크기·인덱스 효율 |

### ★ 공통 컬럼 컨벤션

모든 테이블에:
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- soft delete 필요 시 `deleted_at TIMESTAMPTZ` (NULL=활성)
- 외부 API 데이터: `source_change_date DATE`, `refreshed_at TIMESTAMPTZ` (캐시 무효화 키)
- updated_at 트리거 자동 부착 (slice 00 표준 패턴)

### ★ 인덱스 전략

- B-tree 기본
- **Partial index**: soft delete 친화 (`WHERE deleted_at IS NULL`)
- **GIN**: JSONB 내부 검색 (`patient_actions`, `evidence_summary`)
- **GIN + trigram**: 한글 부분검색 (`disease_master.sick_nm`, `drugs_master.item_name`)
- **복합 인덱스**: 자주 같이 쓰이는 WHERE 컬럼 (`weather_rules(disease_code, weather_alert)`)

### ★ 파티셔닝 정책

`logs.*` 4개 테이블:
- **월별 RANGE 파티션**
- 1년치 데이터 누적 시 수십만~수백만 행 예상
- 오래된 파티션 DROP으로 retention 정책 적용 (DELETE 비용 회피)
- `pg_partman` 또는 cron 잡으로 자동 파티션 생성

### ★ 민감 정보 처리 (PII)

| 컬럼 | 민감도 | 처리 |
|---|---|---|
| `patient_profiles.birthdate` | 중 | 평문 + RLS |
| `patient_profiles.address_*` | 중 | 평문 + RLS (격자 변환 필요) |
| `patient_allergies.*` | 높음 | 평문 + RLS (응급카드에서 토큰 기반 노출) |
| `device_tokens.token` | 높음 | **pgcrypto pgp_sym_encrypt 컬럼 암호화** |
| `patient_medications.notes` | 중 | 평문 + RLS |

**Row-Level Security (RLS)**:
```sql
ALTER TABLE app.patient_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY parent_owner_select ON app.patient_profiles
  FOR SELECT
  USING (parent_id = current_setting('app.current_parent_id', true)::uuid
         OR current_setting('app.role', true) = 'service');
```
앱이 매 요청마다 `SET LOCAL app.current_parent_id = '...'` 주입.

### 외부 의존성 (v6 — v5와 동일)

| 데이터 | 출처 | 갱신 | 상태 |
|---|---|---|---|
| 식약처 의약품 제품 허가정보 | data.go.kr 15095677 | 일 1회 | ✅ 활용 중 |
| 식약처 의약품 낱알식별 정보 | data.go.kr 15057639 | 일 1회 | ✅ 활용 중 |
| HIRA DUR 의약품 목록 | data.go.kr 15127983 | 연 1회 | ✅ 적재 완료 |
| HIRA ATC 코드 매핑 | data.go.kr 15118958 | 연 1회 | ✅ 적재 완료 |
| HIRA 약가기준 | data.go.kr 15054445 | 일 1회 | ✅ 신청 |
| HIRA 질병정보서비스 | data.go.kr 12904 | 연 1회 | ★ slice 05 신청 필요 |
| HIRA 병원·의료기관·약국 정보 | data.go.kr 11999·12101·12100 | 일 1회 | ★ slice 08 신청 필요 |
| 기상청 단기예보 | data.go.kr 1360000 | 매시간 | ✅ 활용 중 |
| 질병관리청 폭염·한파 건강수칙 | KDCA | 연 1회 | ✅ 시드 수집 완료 |
| LLM API | Anthropic/OpenAI | 빌드 타임 | ✅ |

→ slice 05·08용 HIRA 추가 활용신청 필요. 같은 키 재사용 가능.

---

## 테스트 결정 사항 (Testing Decisions)

### 좋은 테스트의 기준 (v1~v5과 동일)

### 회귀 자산 (v6 추가)

| 자산 | 모듈 | 비고 |
|---|---|---|
| (v5까지 자산) | | |
| ★ **DB 스키마 마이그레이션 dry-run 테스트** | 인프라 | `alembic upgrade head` 후 4-schema 16+ 테이블 모두 생성 검증 |
| ★ **RLS 정책 테스트** | 보안 | 자녀 A → 자녀 B의 부모 데이터 조회 시도 시 0행 반환 |
| ★ **컬럼 암호화 라운드트립** | 보안 | device_tokens.token 암호화 후 복호화 일치 |
| ★ **파티션 자동 생성 잡** | 인프라 | 월 변경 시점에 다음달 파티션 자동 생성 |

### v6 신규 테스트 시나리오 (⑨ EmergencyCardModule)

| 시나리오 | 기대 결과 |
|---|---|
| 부모 알레르기 1건 + 약장 3건 + 항응고제(warfarin) 포함 → 응급카드 생성 | snapshot.medications 순서: warfarin 최상단, 알레르기 별도 섹션 최상단 |
| QR 토큰으로 외부 조회 | 200 + access_count 증가 |
| 토큰 만료 후 조회 | 410 Gone |
| 자녀 A의 토큰을 자녀 B가 접근 시도 | 토큰 자체에 권한 — A 토큰만 알면 어디서든 조회 (의도된 동작, rate limit으로 보호) |
| 부모 약장 변경 → 응급카드 자동 갱신 | snapshot_at 갱신, qr_token 유지 |

---

## 범위 밖 (Out of Scope)

v5와 동일 + **응급카드는 더 이상 out of scope 아님 (v6에서 In Scope)**. 응급카드 관련 별도 항목:

1. ~~응급카드 자체~~ → **v6 in scope**
2. **자녀 연락처 관리** (전화번호·이름) — 별도 가족 연결 PRD
3. **QR 코드 이미지 생성·인쇄** — 프론트엔드 영역
4. **응급의료진용 전용 앱·UI**
5. **법무·GDPR 검토** (의료 데이터 외부 노출 정책)

---

## 추가 참고 사항 (Further Notes)

### v6에서 추가 확정된 가정 (★ v6 신규)

v5 가정에 더해:

- **★ DB는 PostgreSQL 14+로 잠금** — JSONB·partial unique·한글 trgm·RLS 모두 활용
- **★ 4-schema 분리로 운영·백업·권한 정책 분리** (ref/app/derived/logs)
- **★ patient_conditions는 정규화 테이블** — FK to disease_master로 무결성 보장
- **★ 알레르기·디바이스 토큰 데이터 모델 신규 정의** — slice 04 책임
- **★ 시계열 로그는 월별 RANGE 파티션** — retention·성능 동시 확보
- **★ RLS + 컬럼 암호화로 민감 의료 데이터 보호**

### v6 운영 비용·성능

- DB 인스턴스: 4-schema라도 단일 PostgreSQL 인스턴스 (개발·MVP). 트래픽 증가 시 read replica 추가
- 파티션 효과: 1년치 logs.*가 12개 파티션으로 분산 → 최근 데이터 쿼리 12배 빠름
- RLS 오버헤드: ~5% (PostgreSQL 14+에서 최적화됨, 운영 측정 후 결정)

### 권장 코드 구현 우선순위 (v6 보정)

| # | 작업 | 의존 |
|---|---|---|
| 1 | DB 인프라 + 4-schema 마이그레이션 + audit trigger 표준 (slice 00) | — |
| 2 | DrugIdentityResolver(①) + PillVisualResolver(⑥) (slice 01) | 1 |
| 3 | PatientProfile + Medications + Allergies + DeviceTokens (slice 04) | 1 |
| 4 | DURRuleEngine(②) (slice 02) | 1 |
| 5 | NBContraindicationExtractor(③) + HallucinationVerifier(⑤) (slice 03) | 1, 2 |
| 6 | SafetyJudge(④) MVP → 확장 (slice 02·03 통합) | 4, 5 |
| 7 | Disease + PatientClass (slice 05) | 3, 5 |
| 8 | WeatherDiseaseAdvisor(⑦) 데이터·룩업 (slice 06) | 3 |
| 9 | Weather ETL + Push (slice 07) | 3, 8 |
| 10 | ★ EmergencyCardModule(⑨) (slice 08) | 3, 7 |
| 11 | API 게이트웨이 통합·노출 | 전부 |

### 다음 단계 (v6 — 슬라이스 v2 + 코드 구현)

| # | 작업 | 상태 |
|---|---|---|
| 1~7 | (v5에서 완료한 데이터 검증·룰셋 큐레이션·기상청 API) | ✅ 완료 |
| **8 (v6 신규)** | **슬라이스 v2 작성 — 블로커 12개 일괄 해결 + 4-schema 반영** | 즉시 진행 |
| **9 (v6 신규)** | **HTML v8 — schema 다이어그램 + 응급카드 흐름 추가** | v2 다음 |
| 10 | 의료진 검수 (17건 우선 큐) | 병렬 |
| 11 | 백엔드 코드 구현 (위 1~11 순서) | 슬라이스 v2 확정 후 |
| 12 | 프론트엔드 PRD (별도) | 백엔드 진행 중 병렬 |

### 관련 데이터 자산 (v6와 동일, v5와 동일)

- (v5까지의 자산 그대로 유지)
- **★ 새로 만들 PRD v6 + 슬라이스 v2 + HTML v8** (이번 작업 결과물)

---

## v6 완료 종합 🏁

| 결정 카테고리 | v6 결정 |
|---|---|
| **DB** | PostgreSQL 14+ + pg_trgm·pgcrypto·btree_gin |
| **스키마 구조** | 4-schema (ref/app/derived/logs) |
| **조인키** | item_seq, sick_cd, parent_id(UUID), nx/ny |
| **PK 전략** | UUID(외부 노출) + BIGSERIAL(내부) + 외부 ID 그대로(stable) |
| **민감 데이터** | RLS + pgcrypto 컬럼 암호화 |
| **시계열 로그** | 월별 RANGE 파티션 |
| **블로커 12개** | 모두 설계 결정으로 해결 → 슬라이스 v2에 반영 |
| **응급카드** | Step 6 복원, 모듈 ⑨ 추가, slice 08 책임 |
| **모듈 수** | 7 운영 + 1 빌드타임 → **9 운영(⑨ 추가) + 1 빌드타임** |
| **사용자 스토리** | 26개 → **28개** (응급카드 자동 갱신·토큰 재발급 추가) |

→ **모든 데이터 검증·아키텍처 결정 완료. 슬라이스 v2 작성 → 코드 구현 단계로 진입.**
