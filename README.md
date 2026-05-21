# 엄마약 (Mom's Med) — Backend

> 2026 보건의료빅데이터·AI 활용 창업경진대회 출품작
> **Java 21 + Spring Boot 4.0.6 + PostgreSQL 14+ + Redis 7+**

자녀가 부모님의 약·기저질환·알레르기를 입력하면 (1) DUR + NB 2겹 약물 안전 검증, (2) 알약 사진 식별, (3) 8질병 × 4단계 = 32 룰 기반 날씨 연계 주의 푸시, (4) 응급실용 QR 카드를 통합 제공한다.

---

## 0. 팀 작업 분담 요약 (★ 가장 먼저 보세요)

> **Track A = 동환** / **Track B = 수종** / **🤝 = 공동 작업**

### 의존성 그래프 + 담당자

```
[🤝 Slice 00 인프라]           ← 둘이 같이 셋업하고 갈라짐
       │
       ├─────────[A] 01 약 마스터 ──┐
       │                            │
       ├─────────[A] 02 DUR ────────┤
       │                            ▼
       ├─────────[A] 03 NB 추출 ── [🤝 SafetyJudge 통합]
       │                            │
       ├─────────[B] 04 부모/약장 ★ ┘   ← 데모 핵심, 04 Controller는 B / SafetyJudge는 A
       │                            │
       ├─────────[B] 05 질병/금기 ──┤
       │                            │
       ├─────────[A] 06 날씨 룰셋 ──┘
       │                            │
       │         [🤝 Slice 07 ETL+푸시]   ← 데이터=A / 부모컨텍스트·푸시=B
       │
       └─────────[B] 08 응급카드
```

### 슬라이스별 담당

| # | 슬라이스 | 담당 | 비고 |
|---|---|---|---|
| 00 | 인프라 + 공통 유틸 | 🤝 **공동** | DDL/Flyway/공통 클래스는 둘이 합의해서 설계 |
| 01 | 약 식별 + 알약 사진 (식약처 API) | **A 동환** | 외부 API 클라이언트 라인 |
| 02 | DUR 병용금기 (837k행 CSV 적재 + 정확매칭) | **A 동환** | 인덱스 튜닝·EXPLAIN 검증 |
| 03 | NB AI 추출 + 환각 검증 (LLM) | **A 동환** | Gemini 클라이언트·프롬프트 |
| 04 | 부모/약장/알레르기/디바이스토큰 (★ 데모 핵심) | **B 수종** + 🤝 통합 | Controller·Service는 B / SafetyJudge 호출 지점은 A와 합의 |
| 05 | 기저질환 + 환자분류 금기 | **B 수종** | HIRA 12904·KCD 매핑 |
| 06 | 날씨 룰셋 + 격자좌표 + 룩업 | **A 동환** | seed JSON·xlsx 적재 |
| 07 | 기상청 ETL + 매일 푸시 (Redis 락) | 🤝 **공동** | 기상청 호출·자체 판정=A / 부모컨텍스트·푸시발송=B |
| 08 | 응급카드 + HIRA 병원/약국 | **B 수종** | QR·Rate Limit·공개 엔드포인트 |

### 주차별 동시 진행 계획 (5주)

| Week | A 동환 | B 수종 | 🤝 공동 |
|---|---|---|---|
| **1** | Slice 01 (식약처 클라이언트 + drugs_master) | Slice 04 도메인 스캐폴드 (PatientProfile/Allergy/DeviceToken 엔티티+CRUD, **SafetyJudge는 mock**) | **Slice 00**: docker-compose, Flyway V001~V003, 공통 유틸 7종, 환경변수 |
| **2** | Slice 02 (DUR 5 CSV 적재 + DurEngineService) | Slice 04 Medication CRUD (mock SafetyJudge로 우선 동작) | API 응답 구조 합의 점검 (HTTP 409 BLOCK 등) |
| **3** | Slice 03 (NB 추출 + 환각 검증 + SafetyJudge 완성) | Slice 05 (HIRA 12904 + patient_conditions 정규화) | **🤝 SafetyJudge 통합** — A가 만든 `SafetyJudgeService.judgeFull()`을 B의 `MedicationController`가 호출하도록 와이어링. HTTP 409 응답 e2e 검증 ★ |
| **4** | Slice 06 (weather_rules 32룰·격자좌표 적재 + lookupRules) | Slice 08 (HIRA 11999/12101/12100 + EmergencyCard + `/em/{token}` 공개 엔드포인트) | — |
| **5** | Slice 07 ETL 부분 (KMA 호출·자체 판정·observations 적재) | Slice 07 푸시 부분 (parent_conditions 조회·device_tokens 복호화·푸시 발송 mock) | **🤝 Slice 07 통합** — A의 ETL 결과를 B의 푸시 잡이 소비. Redisson 락 2단 같이 검증. 데모 시나리오 e2e 리허설 |

### 공동 작업이 필요한 합의 포인트 (🤝)

1. **Slice 00 인프라** — Flyway 마이그레이션 파일 명명 규칙, 4-schema, 공통 엔티티(`BaseTimeEntity`), 예외 계층(`SafetyBlockException`, `ApiException`), 응답 포맷(`ApiResponse<T>`, `BlockErrorResponse`)
2. **`SafetyJudgeService` 인터페이스** — Week 1에 시그니처만 먼저 박아두고 양쪽이 동시 개발 (`Verdict judgeFull(PatientContext, List<Drug> current, Drug newDrug)`)
3. **API 응답 구조** — HTTP 409 BLOCK / 201 ALLOW·WARN 구조 (`HANDOFF_COMMON.md` ground truth)
4. **Slice 07 핸드오프** — `WeatherEtlService.runDaily()` 결과 → `WeatherPushService.dispatch()` 입력 DTO 합의
5. **데모 시나리오 e2e** — 약 추가 BLOCK → 응급카드 → 폭염 시뮬 (Week 5 같이 돌려봄)

> 추가 작업 분담 디테일은 §11 MVP TODO 참고.

---

## 1. 빠른 시작

```bash
# 1) 인프라
docker compose up -d           # PostgreSQL 14 + Redis 7

# 2) DB 스키마 (Flyway)
./gradlew flywayMigrate

# 3) 환경변수 (application-local.yaml 또는 환경변수)
MFDS_API_KEY=...
HIRA_API_KEY=...
KMA_API_KEY=...
GEMINI_API_KEY=...
DATABASE_URL=postgresql://localhost:5432/mom_med
REDIS_URL=redis://localhost:6379
APP_ENCRYPTION_KEY=...           # pgcrypto 컬럼 암호화 마스터키

# 4) 실행
./gradlew bootRun
```

**필수 API 키 4개**: 식약처 / HIRA / 기상청 (전부 data.go.kr 활용신청) / Google Gemini (Google AI Studio).

---

## 2. 기술 스택 (현재 확정)

| 영역 | 선택 | 비고 |
|---|---|---|
| **JDK** | **Java 21** (`toolchain.languageVersion=21`) | Virtual Threads, Pattern Matching, Records 적극 활용 권장 |
| **프레임워크** | **Spring Boot 4.0.6** (Spring Framework 7 기반) | Boot 4에서 `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**로 분리됨 (이미 build.gradle 반영) |
| **빌드** | Gradle | `build.gradle` |
| **DB** | PostgreSQL 14+ | 4-schema 분리, pg_trgm·pgcrypto·btree_gin·pg_stat_statements 확장 |
| **캐시·락** | Redis 7+ | LLM 캐시 / Slice 07 분산 락 2단 |
| **마이그레이션** | Flyway | `src/main/resources/db/migration/V###__*.sql` |
| **DTO·매핑** | Lombok + MapStruct (or Java 21 records) | records 권장 (response DTO) |
| **API 문서** | springdoc-openapi 3.0.2 | 이미 추가됨, `/swagger-ui.html` |
| **LLM** | Gemini 2.5 Flash Lite | `utils/llm_client` Spring WebClient 래퍼 |
| **검증** | spring-boot-starter-validation | 이미 추가됨 |
| **HTTP 클라이언트** | RestClient (Spring 6.1+) 또는 WebClient | Boot 4에서 RestClient가 권장 |

### Spring Boot 4.x 특이사항 (Boot 3 → 4 차이)

- ✅ `spring-boot-starter-web` → **`spring-boot-starter-webmvc`** (build.gradle 이미 적용)
- ✅ 테스트도 `spring-boot-starter-webmvc-test` 사용 (build.gradle 이미 적용)
- Spring Framework 7 기반 — JEP 442 등 최신 Java 기능 호환
- AOT 컴파일 기본 강화 (네이티브 이미지 친화)
- Jakarta EE 11 / Servlet 6.1
- Java 17 이상 필수 (우리는 Java 21)

### 추가 의존성 (현재 build.gradle에 없는 것, Week 1에서 추가 필요)

```gradle
dependencies {
    // 데이터
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    // 분산 락 (Slice 07)
    implementation 'org.redisson:redisson-spring-boot-starter:3.34.1'

    // Rate Limit (Slice 08 — /em/{token})
    implementation 'com.bucket4j:bucket4j-core:8.10.1'

    // 외부 API 호출 (RestClient는 starter-webmvc 포함, 추가 설정만)
    // 또는 WebFlux 클라이언트만:
    // implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // 매핑
    implementation 'org.mapstruct:mapstruct:1.6.3'
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'

    // 테스트
    testImplementation 'org.testcontainers:postgresql:1.20.4'
    testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
}
```

> **partial UNIQUE 인덱스**가 PostgreSQL 특화 기능이라 H2로 테스트하면 안 됨 → **Testcontainers PostgreSQL** 필수.

### Java 21 활용 권장 패턴

- **records로 응답/요청 DTO**: `public record AddMedicationRequest(String itemSeq, String source, String notes) {}`
- **pattern matching switch**: `Verdict.decision`에 따라 응답 분기
- **virtual threads**: Slice 07 ETL에서 격자별 KMA 호출 병렬 — `Executors.newVirtualThreadPerTaskExecutor()`
- **sealed interface**: `Evidence` 계층 (DurEvidence / NbEvidence / AllergyEvidence)

---

## 3. 문서 지도 (어디서 무엇을 찾을지)

| 보고 싶은 것 | 파일 |
|---|---|
| 전체 비전, 사용자 스토리, 4-schema 청사진 | `PRD_엄마약_v6.md` |
| **개발 슬라이스별 상세 명세 (DDL · API · 의사코드)** | `issues_v2/00 ~ 08-*.md` |
| 슬라이스 의존성 그래프 · 권장 작업 순서 | `issues_v2/README.md` |
| 프론트와의 응답구조 합의 (HTTP 409 BLOCK 등) | `HANDOFF_COMMON.md` |
| 프론트엔드가 기대하는 응답 JSON Zod 스키마 | `api-types/*.ts` |
| 시각화 다이어그램 (브라우저로 열어보기) | `엄마약_API_흐름도_v8.html` |
| DUR/식약처 OpenAPI 호출 가이드 | `data/OpenAPI활용가이드_*.pdf` |
| 기상청 단기예보 API 가이드 + 격자좌표 xlsx | `기상청41_단기예보_*/` |
| 날씨 룰셋 32건 + 환각 검증 ground truth | `seed/` |
| LLM NB 추출 회귀 테스트 fixture | `nb_amlodipine.*`, `nb_warfarin.*` |

> **충돌 시 ground truth 우선순위**:
> API 응답 구조 = `api-types/*.ts` → DB DDL = `issues_v2/*.md` → 기능 범위 = `PRD_엄마약_v6.md` → 양팀 합의 = `HANDOFF_COMMON.md`

---

## 4. 핵심 아키텍처 결정 (PRD v6에서 잠금)

### 4.1 DB는 PostgreSQL 14+ — 4-schema 분리

```
mom_med database
├── ref      외부 마스터 (식약처·HIRA·기상청 적재)         ← SELECT만, 백업 적게
├── app      사용자 데이터 (PII, RLS, PITR 백업 필수)     ← 운영의 핵심
├── derived  LLM 가공 캐시 (재호출로 복구 가능)
└── logs     시계열 (월별 RANGE 파티션, append-only)
```

**필수 PostgreSQL 확장**: `pg_trgm`, `pgcrypto`, `btree_gin`, `pg_stat_statements`

### 4.2 보안

- **RLS** (Row-Level Security): 매 요청마다 `SET LOCAL app.current_parent_id = '...'` 주입
- **컬럼 암호화**: `device_tokens.token_encrypted = pgp_sym_encrypt(token, :key)`
- **공개 엔드포인트** `GET /em/{token}`: 인증 없음 — 반드시 Rate Limit + 90일 만료 + revoke 지원

### 4.3 응답 구조 ground truth (★ 프론트와 합의, 변경 금지)

**약 추가 시 BLOCK (HTTP 409)**:
```json
{
  "error": "block",
  "verdict": {
    "decision": "BLOCK",
    "evidences": [
      { "source":"DUR", "severity":"high", "message":"...", "citation":"...", "citation_source":"..." }
    ]
  }
}
```

**약 추가 시 ALLOW / WARN (HTTP 201)**:
```json
{
  "medication_id": "uuid",
  "item_seq": "200610660",
  "safety_check": { "decision":"ALLOW"|"WARN", "evidences": [...] }
}
```

**응급카드 QR URL**: `https://app.엄마약.com/em/{token}` (← `/c/{token}` 아님)

### 4.4 enum 정리 (혼동 주의)

| 도메인 | 값 | 사용처 |
|---|---|---|
| 약물 안전판정 | `high` / `medium` / `low` (영문) | Slice 02·03·04 |
| 날씨 알림 | `관심` / `주의` / `경고` / `위험` (한글) | Slice 06·07 |
| 알레르기 | `severe` / `moderate` / `mild` | Slice 04 |
| 안전 판정 결과 | `BLOCK` / `WARN` / `INFO` / `ALLOW` | Slice 04 응답 |
| 푸시 송신 상태 | `sent` / `failed` / `simulated` / `skipped_fatigue` / `skipped_review` | Slice 07 |

### 4.5 항응고제 판별 (양팀 동일 규약)

```java
public static boolean isAnticoagulant(String atcCode) {
    return atcCode != null && atcCode.startsWith("B01A");
}
```

---

## 5. 슬라이스 빌드 순서 (★ 작업 흐름의 핵심)

```
00 인프라 (4-schema, LLM 클라이언트, 환각 검증기)        🤝 공동
 ↓
01 약 마스터 + 알약 이미지 (식약처 15095677·15057639)      A 동환
 ↓
02 DUR 병용금기 (HIRA 11983, 837k행, 정확매칭 ★)         A 동환
 ↓
03 NB 추출 (LLM 2겹 안전망, 환각 검증)                    A 동환
 ↓
04 부모 약장 + 안전판정 통합 (★ 가장 큰 슬라이스)         B 수종 + 🤝 통합
 ↓
05 질병·환자분류 금기 (KCD 매핑)                          B 수종
 ↓
06 날씨 룰셋 32개 + 격자좌표 + 룩업                       A 동환
 ↓
07 매일 ETL + 자체 판정 + 푸시 (Redis 락 2단)            🤝 공동
 ↓
08 응급카드 + HIRA 11999/12101/12100                     B 수종
```

---

## 6. 슬라이스 요약 (각 `issues_v2/*.md` 압축)

> 각 슬라이스 문서가 ground truth. 아래는 인덱스 + 핵심 발췌.

### Slice 00 — 4-schema 인프라 + 공유 유틸 🤝 [→ `issues_v2/00-foundation-v2.md`]

**담당 분담**:
- **동환**: `DrugNameNormalizer` (15 염 목록), `HallucinationVerifier` (3단계 매칭), `GeminiClient` (RestClient/WebClient)
- **수종**: `BaseTimeEntity`, `GlobalExceptionHandler`, `ApiResponse<T>`/`BlockErrorResponse`, `RedisCacheService`, `PgCryptoService`
- **공동**: Flyway 마이그레이션 (V001 extensions / V002 schemas / V003 audit trigger), docker-compose, application.yaml, 환경변수 7개, `SafetyJudgeService` 인터페이스 시그니처 박아두기

**Flyway 마이그레이션 3종**:
```sql
-- V001__extensions.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- V002__schemas.sql
CREATE SCHEMA IF NOT EXISTS ref;
CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS derived;
CREATE SCHEMA IF NOT EXISTS logs;
ALTER DATABASE mom_med SET search_path TO app, ref, derived, logs, public;

-- V003__audit_trigger.sql
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;
```

**수락 기준**: docker compose up → PG+Redis 정상, Flyway 후 `\dn`에 4 schema, `DrugNameNormalizer` 단위테스트 6 케이스, `GeminiClient.call("ping")` 성공

---

### Slice 01 — 약 식별 + 알약 사진 [A 동환] [→ `issues_v2/01-drug-identity-pill-visual-v2.md`]

**외부 API 3개 (식약처)**:
| API | sno | 용도 |
|---|---|---|
| 의약품 제품 허가정보 [목록] | 15095677 | `item_name` → `ITEM_SEQ` 매칭 |
| 의약품 제품 허가정보 [상세] | 15095677 | `ITEM_SEQ` → 성분/ATC/NB_DOC_DATA |
| 의약품 낱알식별 정보 | 15057639 | `ITEM_NAME` → 알약 사진·각인·색·모양 |

**테이블**: `ref.drugs_master` (PK item_seq), `ref.pill_visuals` (PK item_seq FK)

**핵심 컬럼**: `main_ingr_norm` ← `DrugNameNormalizer(main_ingr_en)` — Slice 02 DUR 조인 키

**API**: `GET /v1/drugs/identify?name={user_input}` → 200/300(동명이품)/404

**수락 기준**: 타이레놀500 → `item_seq=202106092`, `main_ingr_norm="acetaminophen"`, 2회 호출 시 외부 API 0회

---

### Slice 02 — DUR 1차 병용금기 (정확매칭) [A 동환] [→ `issues_v2/02-dur-coadministration-check-v2.md`]

**외부 API 0건**. CSV 5개 적재:
| 테이블 | 행수 |
|---|---|
| `ref.dur_combo_contraindications` | **837,837** (231MB, cp949) |
| `ref.dur_elderly_caution` | 572 |
| `ref.dur_elderly_nsaid_caution` | 1,083 |
| `ref.dur_age_contraindication` | 2,905 (Slice 05에서 적재) |
| `ref.dur_pregnancy_contraindication` | 19,015 (Slice 05에서 적재) |

**⚠ 핵심**: `LIKE '%X%'` 절대 금지. **`ingredient_norm = ?` 정확 매칭** + `idx_dur_combo_pair(a_norm, b_norm)` 인덱스

**SafetyJudge MVP** (동환이 인터페이스 구현):
- 병용금기 매칭 → BLOCK / 65세↑ + 노인주의 → WARN / 그 외 → ALLOW
- Dedupe: (성분A, 성분B) 짝 기준

**수락 기준**: amlodipine+itraconazole → BLOCK, amlodipine+simvastatin → ALLOW (Slice 03에서 회수), 응답 < 50ms, EXPLAIN 인덱스 hit

---

### Slice 03 — NB AI 추출 + 2겹 안전망 [A 동환] [→ `issues_v2/03-nb-extraction-2layer-safety-v2.md`]

**외부 API**: Gemini 2.5 Flash Lite (RestClient/WebClient)

**테이블**: `derived.nb_extractions`, `derived.nb_interactions` (★ `entry_type CHECK ('drug_drug' | 'patient_class')` 처음부터 정의)

**risk_level enum 매핑 → 통합 판정**:
| 본문 표현 | enum | 판정 |
|---|---|---|
| "투여하지 말 것", "금기" | `동시투여피해야함` | BLOCK |
| "권장하지 않는다" | `권장하지않음` | WARN |
| "주의깊게 관찰" | `주의` | INFO |
| "영향이 없었다" | `정보만` | (UI 노출 안 함) |

**약물군 → ATC prefix 매핑** (NB가 "CYP3A4 저해제" 같은 군명 반환 시):
```java
Map.of("CYP3A4 저해제", List.of("J02AC","J01FA09"),
       "비스테로이드성 소염제", List.of("M01A"),
       "항응고제", List.of("B01A"))
```

**HallucinationVerifier**: 3단계 (exact → normalized → fuzzy 40자). 실패 시 1회 재시도, 그래도 실패하면 `verified=false`, 사용자 노출 금지.

**`SafetyJudgeService.judgeFull()` 완성** (★ 수종의 04와 통합 지점):
1. DUR (Slice 02) + 2. 새 약 NB ↔ 기존 약 + 3. 기존 약 NB ↔ 새 약

**수락 기준**: 노바스크 39 entry / 워파린 46 entry / 환각 0건, 노바스크+심바스타틴 → NB로 WARN 회수

---

### Slice 04 — 부모 + 약장 + 알레르기 + 디바이스 토큰 ★ [B 수종 + 🤝 통합] [→ `issues_v2/04-parent-medication-management-v2.md`]

가장 큰 슬라이스. **공모전 데모의 핵심**.

**테이블 4종 (`app.*`)**:
| 테이블 | PK | 핵심 |
|---|---|---|
| `app.patient_profiles` | `parent_id UUID` | birthdate, sex, nx/ny, is_pregnant |
| `app.patient_medications` | `id BIGSERIAL` | soft delete, **partial UNIQUE(parent_id, item_seq) WHERE deleted_at IS NULL** |
| `app.patient_allergies` | `id BIGSERIAL` | `allergen_norm` (약물만 정규화) |
| `app.device_tokens` | `id BIGSERIAL` | **`token_encrypted BYTEA`** (`pgp_sym_encrypt`) |

`logs.safety_check_log` (월별 파티션) — 모든 약 추가 시도 기록 (BLOCK도 포함)

**🤝 통합 지점 (Week 3)** — 수종 ↔ 동환:
- 수종이 `MedicationController.addMedication()`을 만듦
- 동환이 만든 `SafetyJudgeService.judgeFull(parentCtx, currentDrugs, newDrug)`을 호출
- Week 1엔 수종이 mock 구현체로 우선 동작 (`return Verdict.ALLOW;`)
- Week 3에 실제 구현체 swap → e2e BLOCK 응답 검증

**핵심 API** ★:
```
POST /v1/parents/{parentId}/medications
  내부:
    1. ref.drugs_master에서 item_seq 조회 (없으면 Slice 01 호출)
    2. SafetyJudgeService.judgeFull(parentCtx, currentDrugs, newDrug)   ← 동환 코드
    3. logs.safety_check_log INSERT (BLOCK도 기록)                      ← @Transactional(REQUIRES_NEW)
    4. BLOCK → SafetyBlockException 발생 → HTTP 409
       WARN/ALLOW → INSERT + HTTP 201
```

**트랜잭션 주의**: log 기록은 `@Transactional(propagation=REQUIRES_NEW)` 또는 ApplicationEvent로 분리해 BLOCK rollback 시에도 log는 남기기.

**수락 기준**: HTTP 409 BLOCK 응답, partial UNIQUE 위반, 토큰 평문 DB 미저장 (단위테스트), consent_data_share=false → 403

---

### Slice 05 — 기저질환 + 환자분류 금기 [B 수종] [→ `issues_v2/05-disease-patient-class-contraindication-v2.md`]

**외부 API**: HIRA 12904 (질병정보서비스) — XML 응답. KCD 검증용.

**테이블**:
- `ref.disease_master` (PK sick_cd) — 47,798행 (HIRA 11984 CSV)
- `app.patient_conditions` — 정규화 (JSONB 옵션 거부), partial UNIQUE(parent_id, disease_code) WHERE deleted_at IS NULL

**KCD 매핑 사전** (LLM 프롬프트 확장으로 patient_class entry 추출):
| NB 본문 표현 | KCD |
|---|---|
| "간장애 환자" | K70-K77 |
| "신장(콩팥)장애 환자" | N17-N19 |
| "심장기능저하" | I50, I20-I25 |
| "소화성궤양" | K25-K27 |
| "혈액 이상" | D60-D64, D70-D77 |
| "당뇨 환자" | E10-E14 |

**🤝 합의 필요**: 동환의 Slice 03 LLM 프롬프트에 `entry_type='patient_class'` 추출 항목 추가 (수종이 프롬프트 PR 함께 검토)

**SafetyJudge 확장 (3차 안전망)**:
- `checkPatientClassContraindication` / `checkAgeContraindication` / `checkPregnancyContraindication` / `checkAllergy`

**수락 기준**: 부모 K25 + NSAID → WARN, 부모 페니실린 알레르기 + amoxicillin → BLOCK, 부모 임신 + 임부금기 → BLOCK

---

### Slice 06 — 날씨 룰셋 + 격자 좌표 + 룩업 [A 동환] [→ `issues_v2/06-weather-ruleset-lookup-v2.md`]

**외부 API 0건**. JSON/xlsx 시드 적재.

**테이블**:
- `ref.weather_rules` — 32행 (`seed/weather_rules_v0.2.json`), severity CHECK IN ('관심','주의','경고','위험')
- `ref.region_grid` — 26K+ (xlsx)

**`updateParentGrid(parentId)`** — 1순위 시·구·동 정확매칭, 2순위 시·구 fallback (도농복합지역 격자 정확도)

**WeatherAdvisory DTO**: `rule_id` 필수 포함 (Slice 07 dedup용 — v6 블로커 9번)

**🤝 핸드오프**: `lookupRules(diseaseCodes, alerts)` 시그니처를 Week 4에 미리 합의 → Week 5에 수종이 호출

**수락 기준**: 대구 중구 → nx=89,ny=90, `lookupRules(["I10","E11"], ["폭염경보"])` → 2 advisory + rule_id

---

### Slice 07 — 기상청 ETL + 매일 푸시 🤝 [→ `issues_v2/07-weather-etl-daily-push-v2.md`]

이 슬라이스는 **A·B 둘 다 손대는 통합 슬라이스**.

**A 동환 담당 (데이터 라인)**:
- `KmaForecastClient` — `getVilageFcst` 호출
- TMX/TMN 추출 + 자체 임계값 판정 (TMX≥35 → 폭염경보 등)
- `logs.weather_observations_daily` 적재
- `@Scheduled(cron="0 30 5 * * *", zone="Asia/Seoul")` 잡 — Java 21 Virtual Threads로 격자별 병렬 호출
- **잡 락**: Redisson `RLock` (`weather_etl:job:{date}`, TTL 30분)

**B 수종 담당 (푸시 라인)**:
- 활성 부모 조회 (`consent_data_share=true`, nx/ny 있음)
- `app.patient_conditions` JOIN으로 disease_codes
- `lookupRules()` 호출 (← 동환의 Slice 06)
- 우선순위 정책 (severity 위험 우선 1푸시/일)
- 알람 피로 방지 + `requires_review` skip
- `device_tokens` 복호화 (`pgp_sym_decrypt`) → 푸시 발송 (mock)
- `logs.advisory_push_log` 적재 + `delivery_status` 5종 분기
- **부모 락**: Redisson `RLock` (`weather_etl:parent:{pid}:{date}`, TTL 5분)

**🤝 합의 포인트**:
- `WeatherEtlService.runDaily()` 결과 DTO (관측 + alerts 리스트)
- `WeatherPushService.dispatch(observation, parents)` 시그니처
- Redisson 설정·키 네임스페이스
- 통합 e2e: TMX=36 mock → 폭염경보 → I10 부모에 푸시 1건

**자체 임계값**:
| 조건 | derived_alerts |
|---|---|
| TMX ≥ 35℃ | 폭염경보 |
| 33 ≤ TMX < 35 | 폭염주의보 |
| TMN ≤ -15℃ | 한파경보 |
| -15 < TMN ≤ -12 | 한파주의보 |

**수락 기준**: TMX=36 mock → 폭염경보+푸시 1건, 같은 (parent,rule,date) 2번째 → `skipped_fatigue`, I10(위험)+E11(주의) → I10 1푸시만, ETL 2번 트리거 → `skipped_duplicate_run`

---

### Slice 08 — 응급카드 [B 수종] [→ `issues_v2/08-emergency-card-v2.md`]

**외부 API 3개 (HIRA)**:
| API | sno | 용도 |
|---|---|---|
| 병원정보서비스 | 11999 | yadmNm/ykiho |
| 의료기관별상세정보 | 12101 | 응급실 운영 |
| 약국정보서비스 | 12100 | 단골 약국 |

**테이블**: `app.parent_hospitals`, `app.hospital_emergency_info`, `app.parent_pharmacies`, `app.emergency_cards` (PK parent_id, UNIQUE qr_token)

**snapshot 우선순위**:
1. **알레르기** (severity DESC) ★ TOP
2. **항응고제** `atc_code LIKE 'B01A%'` ★ 2순위
3. 기저질환 (Slice 05 JOIN)
4. 전체 약장 (항응고제 최상단)
5. 단골 병원·약국

**QR 토큰**: Java `SecureRandom` 48바이트 → Base64URL ≈ 64자

**`access_count` 원자적 증가**:
```sql
UPDATE app.emergency_cards SET access_count = access_count + 1, last_accessed_at = NOW()
WHERE qr_token = :t;
```

**Rate Limit (Bucket4j + Redis)**:
- IP당 분당 10회 / 토큰당 시간당 60회 / 토큰당 일일 200회 (초과 시 자동 revoke)

**핵심 API**:
```
POST /v1/parents/{parentId}/emergency-card/regenerate    → 200 { qr_token, qr_url:"/em/{token}", valid_until }
POST /v1/parents/{parentId}/emergency-card/rotate        → 200 (new token, old revoked)
POST /v1/parents/{parentId}/emergency-card/revoke        → 204
GET  /em/{token}                                          → 200 snapshot / 410 Gone / 429 Too Many
```

**수락 기준**: 항응고제 정렬, access_count 원자 증가 (동시성 부하 테스트), 만료 후 410, revoke 후 즉시 410, Rate limit → 429 + 자동 revoke

---

## 7. Spring Boot 패키지 구조 (도메인 중심)

```
mamokey.mom_med.backend
├── BackendApplication.java
│
├── global/                      🤝 공동
│   ├── config/          SecurityConfig, JpaConfig, RedisConfig, OpenApiConfig, RestClientConfig
│   ├── exception/       GlobalExceptionHandler, SafetyBlockException, ApiException
│   ├── response/        ApiResponse, ErrorResponse, BlockErrorResponse  (record)
│   ├── util/            DrugNameNormalizer[A], HallucinationVerifier[A], KcdRangeExpander[B]
│   ├── audit/           BaseTimeEntity
│   └── ratelimit/       Bucket4j 설정 (Slice 08)
│
├── infra/
│   ├── cache/           RedisCacheService                              [B 또는 공동]
│   ├── crypto/          PgCryptoService (pgp_sym_encrypt/decrypt)      [B]
│   ├── llm/             GeminiClient (RestClient 또는 WebClient)        [A]
│   ├── lock/            RedissonLockService (Slice 07)                  [공동]
│   └── scheduler/       WeatherEtlScheduler (@Scheduled)                [A 트리거]
│
├── external/
│   ├── mfds/            MfdsClient (식약처 3 API)                       [A]
│   ├── hira/            HiraDurClient · HiraHospitalClient · HiraPharmacyClient · HiraDiseaseClient   [HiraDur=A / Hospital·Pharmacy·Disease=B]
│   └── kma/             KmaForecastClient                               [A]
│
└── domain/
    ├── drug/            Slice 01 — Drug, PillVisual, DrugController     [A]
    ├── dur/             Slice 02 — DurCombo*, DurEngineService          [A]
    ├── nb/              Slice 03 — NbExtraction, NbInteraction          [A]
    ├── safety/          Slice 02+03 — SafetyJudgeService, Verdict, Evidence (sealed)   [A 구현 / B 호출]
    ├── parent/          Slice 04 — PatientProfile, ParentController     [B]
    ├── medication/      Slice 04 — PatientMedication, MedicationController, SafetyBlockException   [B]
    ├── allergy/         Slice 04 — PatientAllergy                       [B]
    ├── device/          Slice 04 — DeviceToken (pgcrypto)               [B]
    ├── condition/       Slice 05 — PatientCondition, DiseaseMaster      [B]
    ├── weather/         Slice 06+07 — WeatherRule, RegionGrid, WeatherAdvisorService [A] / WeatherEtlService [A] / WeatherPushService [B]
    ├── emergency/       Slice 08 — EmergencyCard, ParentHospital, ParentPharmacy, PublicEmController   [B]
    └── log/             SafetyCheckLog, AdvisoryPushLog, ApiCallLog (logs.*)  [각자 자기 슬라이스 책임]
```

---

## 8. 전체 API 목록 (인증 ✅ = 자녀 세션 필요)

| Method | URL | 슬라이스 | 담당 | 인증 |
|---|---|---|---|---|
| GET | `/v1/drugs/identify?name=` | 01 | A | ✅ |
| GET | `/v1/drugs/{itemSeq}/contraindications` | 03 | A | ✅ |
| POST | `/v1/parents` | 04 | B | ✅ |
| GET / PATCH / DELETE | `/v1/parents/{id}` | 04 | B | ✅ |
| GET | `/v1/parents/{id}/medications` | 04 | B | ✅ |
| POST | `/v1/parents/{id}/medications` ★ | 04 | B (+🤝 SafetyJudge) | ✅ |
| DELETE | `/v1/parents/{id}/medications/{medId}` | 04 | B | ✅ |
| POST / GET / DELETE | `/v1/parents/{id}/allergies` | 04 | B | ✅ |
| POST / DELETE | `/v1/parents/{id}/device-tokens` | 04 | B | ✅ |
| POST / GET / DELETE | `/v1/parents/{id}/conditions[/{id}]` | 05 | B | ✅ |
| POST | `/v1/safety/check` | 02/03/05 | A | ✅ |
| GET | `/v1/parents/{id}/weather-advisory` | 06/07 | B (Controller) ← A (룩업) | ✅ |
| POST | `/v1/parents/{id}/hospitals` | 08 | B | ✅ |
| POST | `/v1/parents/{id}/pharmacies` | 08 | B | ✅ |
| POST | `/v1/parents/{id}/emergency-card/regenerate` | 08 | B | ✅ |
| POST | `/v1/parents/{id}/emergency-card/rotate` | 08 | B | ✅ |
| POST | `/v1/parents/{id}/emergency-card/revoke` | 08 | B | ✅ |
| **GET** | **`/em/{token}`** | **08** | **B** | **❌ Rate Limit** |
| POST | `/v1/admin/weather/etl-run` | 07 | A | 관리자 |
| GET | `/v1/admin/weather/recent-pushes?days=` | 07 | B | 관리자 |
| GET | `/v1/admin/weather-rules?status=needs_review` | 06 | A | 관리자 |
| POST | `/v1/admin/weather-rules/{id}/approve` | 06 | A | 관리자 |

---

## 9. 데이터 적재 체크리스트

| 슬라이스 | 데이터 | 위치 | 크기 | 담당 |
|---|---|---|---|---|
| 02 | DUR 5개 CSV | `data/건강보험심사평가원_의약품안전사용서비스(DUR)*/` | **240MB** (병용금기 231MB 별도 다운로드) | A |
| 01·02 | ATC 매핑 | `data/건강보험심사평가원_ATC코드 매핑 목록_20250630.csv` | 2.4MB | A |
| 05 | 질병마스터 | HIRA 11984 별도 다운로드 | — | B |
| 06 | 날씨 룰셋 32개 | `seed/weather_rules_v0.2.json` | 76KB | A |
| 06 | 격자 좌표 26K | `기상청41_단기예보_*/격자_위경도(2510).xlsx` | 680KB | A |
| 03 회귀 | NB 추출 골든 | `nb_amlodipine.*`, `nb_warfarin.*` | 66KB | A |

**231MB CSV 출처**: HIRA 공공데이터포털 sno 11983 (data.go.kr/data/15095677)

---

## 10. 주의사항

### 10.1 MVP 범위 밖 (mock 처리, 별도 PRD)
- 카카오 OAuth · SMS 본인인증 → "인증·세션 PRD"
- 카카오톡 알림톡 / FCM·APNs 실제 발송 → "푸시 인프라 PRD"
- 처방전 OCR → "OCR PRD"
- 의료진 검수 어드민 → "관리자 PRD"

MVP에서 자녀 세션은 `X-Parent-Owner` 헤더 또는 mock JWT로 대체. (수종이 인증 mock 인터셉터 설치)

### 10.2 DB·인프라 위험
- **DUR 837k행 LIKE 금지** → `ingredient_norm = ?` 정확매칭 (A 동환)
- **partial UNIQUE는 PostgreSQL 특화** → Testcontainers PostgreSQL 필수 (H2 ❌)
- **월별 RANGE 파티션** 자동 생성 잡 — `pg_partman` or Spring `@Scheduled`로 다음달 미리 생성 (공동, 04·07 사용)
- **Spring Boot 4.x**: `spring-boot-starter-web` → `webmvc` (이미 적용), Java 17 미만 호환 불가 — Java 21 사용

### 10.3 보안·트랜잭션
- 약 추가: `safety_check_log INSERT`는 `@Transactional(REQUIRES_NEW)` 또는 ApplicationEvent로 BLOCK rollback과 분리 (B 수종)
- `device_tokens` 항상 `pgp_sym_encrypt`/`decrypt` 네이티브 쿼리. JPA AttributeConverter로도 구현 가능 (B 수종)
- `/em/{token}` Bucket4j + Redis Rate Limit + `Cache-Control: no-store` (B 수종)
- RLS는 인증 시스템 붙은 후 인터셉터에서 `SET LOCAL app.current_parent_id` 자동 주입 (공동)

### 10.4 LLM 환각 방지 (A 동환)
- `source_quote`가 NB 원문에 byte-for-byte 존재하지 않으면 사용자 노출 금지
- `verified=false` 데이터는 SELECT에서 제외
- 환각 검증 단위테스트 필수 (Slice 00 수락기준)

---

## 11. MVP TODO 리스트 (담당자별)

### 🥇 Week 1 — 인프라 + 슬라이스 01·04 스캐폴드

#### 🤝 공동 (Day 1~2 같이 페어 권장)
- [ ] build.gradle 의존성 추가 (data-jpa, data-redis, flyway, redisson, bucket4j, mapstruct, testcontainers)
- [ ] `docker-compose.yml` — PostgreSQL 14 + Redis 7
- [ ] Flyway: `V001__extensions.sql`, `V002__schemas.sql`, `V003__audit_trigger.sql`
- [ ] `application.yaml` + 환경변수 7개 + `application-local.yaml`
- [ ] `BaseTimeEntity` (`@MappedSuperclass`, created_at/updated_at)
- [ ] `GlobalExceptionHandler` + `ApiResponse<T> record` + `ErrorResponse record` + `BlockErrorResponse record`
- [ ] `SafetyBlockException`, `ApiException`
- [ ] **`SafetyJudgeService` 인터페이스 시그니처 합의 후 박아두기** (Week 3 통합 대비)
- [ ] CI (GitHub Actions: build, test, Flyway dry-run)

#### A 동환
- [ ] `DrugNameNormalizer` (15 염 목록) + 6 케이스 단위테스트
- [ ] `HallucinationVerifier` (3단계 매칭) + 단위테스트
- [ ] `GeminiClient` (RestClient) + ping 통합테스트
- [ ] Slice 01 시작: `MfdsClient` 골격 + `Drug` 엔티티

#### B 수종
- [ ] `RedisCacheService` (read-through, fallback)
- [ ] `PgCryptoService` (네이티브 쿼리)
- [ ] Slice 04 시작: `PatientProfile`, `PatientMedication`, `PatientAllergy`, `DeviceToken` 엔티티 + Repository
- [ ] `ParentController`, `MedicationController` 스캐폴드 (**SafetyJudge mock으로 우선 동작**)

---

### 🥇 Week 2 — Slice 02 + Slice 04 Medication CRUD

#### A 동환 — Slice 02
- [ ] DUR 5 CSV 적재 (Spring Batch Job 또는 `CommandLineRunner`) — cp949 인코딩
- [ ] `ref.dur_*` 5 테이블 + ingredient_norm 인덱스 마이그레이션
- [ ] ATC 매핑 CSV 적재
- [ ] `DurEngineService` 정확매칭 (`ingredient_norm = ?`)
- [ ] `SafetyJudgeService` MVP 구현 (DUR + 노인주의만, NB는 다음 주)
- [ ] EXPLAIN으로 인덱스 hit 검증
- [ ] amlodipine+itraconazole BLOCK 통합테스트

#### B 수종 — Slice 04 CRUD
- [ ] `ParentController` CRUD 완성 (POST/GET/PATCH/DELETE)
- [ ] `MedicationController.addMedication` — mock SafetyJudge로 동작
- [ ] `AllergyController` CRUD
- [ ] `DeviceTokenController` + pgcrypto 암호화 단위테스트
- [ ] `logs.safety_check_log` 월별 파티션 + 첫 파티션 마이그레이션
- [ ] partial UNIQUE 위반 통합테스트 (Testcontainers)
- [ ] soft delete 통합테스트

#### 🤝 공동 점검
- [ ] HTTP 409 BLOCK 응답 구조 점검 (`api-types/safety.ts`와 정합)

---

### 🥇 Week 3 — Slice 03 + Slice 05 + SafetyJudge 통합 ★

#### A 동환 — Slice 03
- [ ] NB 추출 LLM 파이프라인 (`NbExtractionService.extract(itemSeq)`)
- [ ] `derived.nb_extractions`, `derived.nb_interactions` 테이블 (entry_type 처음부터 포함)
- [ ] HallucinationVerifier 통합 (실패 시 1회 재시도, `verified=false` 제외)
- [ ] 약물군 → ATC prefix 매핑 사전
- [ ] **`SafetyJudgeService.judgeFull()` 완성** (DUR + NB 양방향)
- [ ] 회귀: amlodipine 39 entry / warfarin 46 entry / 환각 0

#### B 수종 — Slice 05
- [ ] `HiraDiseaseClient` (HIRA 12904 XML)
- [ ] `ref.disease_master` CSV 적재 (47,798행)
- [ ] `ConditionController` + `app.patient_conditions` 정규화
- [ ] KCD 매핑 사전 + `KcdRangeExpander` 유틸 (`K70-K77` → set 확장)

#### 🤝 통합 (Week 3 후반) ★ — 데모 핵심
- [ ] A의 `SafetyJudgeService` 실구현 → B의 `MedicationController`가 호출하도록 swap
- [ ] e2e: 노바스크+심바스타틴 추가 → 409 응답 검증
- [ ] e2e: 부모 K25 + NSAID 추가 → WARN 응답 검증
- [ ] e2e: 부모 페니실린 알레르기 + amoxicillin → 409 BLOCK 검증
- [ ] A·B 페어 PR review

---

### 🥈 Week 4 — Slice 06 + Slice 08

#### A 동환 — Slice 06
- [ ] `weather_rules_v0.2.json` 32 룰 적재 (`ref.weather_rules`)
- [ ] 격자좌표 xlsx 26K 적재 (`ref.region_grid`) — Apache POI
- [ ] `WeatherAdvisorService.lookupRules(diseaseCodes, alerts)` (rule_id 포함)
- [ ] `WeatherAdvisorService.updateParentGrid(parentId)` (동 우선 매칭)
- [ ] 의료진 검수 어드민 API (`needs_review` 큐, 17건)

#### B 수종 — Slice 08
- [ ] `HiraHospitalClient` / `HiraPharmacyClient` (XML)
- [ ] `parent_hospitals`, `hospital_emergency_info`, `parent_pharmacies`, `emergency_cards` 테이블
- [ ] `EmergencyCardService.regenerate()` — 항응고제 정렬, snapshot JSON 생성
- [ ] `PublicEmController.getByToken()` — `GET /em/{token}` + Bucket4j Rate Limit + Redis 카운터
- [ ] 토큰 회전·revoke·만료 시나리오
- [ ] `access_count` 원자 증가 동시성 테스트 (JMeter or 가벼운 부하 테스트)

#### 🤝 합의
- [ ] `lookupRules` 시그니처 확정 (Week 5 ETL 통합 대비)

---

### 🥈 Week 5 — Slice 07 통합 ETL + 데모 리허설

#### A 동환 — ETL 데이터 라인
- [ ] `KmaForecastClient.getVilageFcst(nx, ny, baseDate, baseTime)`
- [ ] TMX/TMN 추출 + 자체 임계값 판정 로직
- [ ] `logs.weather_observations_daily` 파티션 적재
- [ ] `@Scheduled(cron="0 30 5 * * *", zone="Asia/Seoul")` 잡 — Java 21 Virtual Threads로 격자 병렬 호출
- [ ] Redisson 잡 락 (`weather_etl:job:{date}`)

#### B 수종 — 푸시 라인
- [ ] 활성 부모 조회 + 격자 그룹핑
- [ ] `app.patient_conditions` JOIN → A의 `lookupRules()` 호출
- [ ] 우선순위 정책 (severity 위험 우선 1푸시/일)
- [ ] 알람 피로 방지 + `requires_review` skip → `delivery_status` 5종 분기
- [ ] `device_tokens` 복호화 → 푸시 발송 (mock)
- [ ] `logs.advisory_push_log` 파티션 적재
- [ ] Redisson 부모 락 (`weather_etl:parent:{pid}:{date}`)

#### 🤝 Week 5 후반 — 통합 + 데모 리허설
- [ ] ETL → 푸시 e2e: TMX=36 mock → 폭염경보 → I10 부모 푸시 1건
- [ ] 알람 피로: 같은 (parent,rule,date) 2번째 호출 → `skipped_fatigue`
- [ ] 우선순위: I10(위험)+E11(주의) → I10만
- [ ] 중복실행: ETL 2회 트리거 → 2번째 `skipped_duplicate_run`
- [ ] **공모전 데모 시나리오 e2e 리허설** (HANDOFF_COMMON.md §13):
  1. 부모 등록 → 약 4개 추가 (warfarin 포함)
  2. 아스피린 추가 → 409 BLOCK 모달
  3. 응급카드 QR → `/em/{token}` 외부 조회 → critical_drugs에 warfarin
  4. 폭염경보 시뮬 → I10 매칭 advisory
- [ ] 프론트와 통합 (`EXPO_PUBLIC_USE_MOCK=false`)

---

### 🥉 Week 6+ — 고도화 (담당 추후 정함)
- [ ] RLS 활성화 + 인증 인터셉터
- [ ] 카카오 OAuth (별도 PRD)
- [ ] FCM/APNs 실제 발송
- [ ] 처방전 OCR 통합
- [ ] 의료진 검수 어드민
- [ ] Prometheus + Grafana 대시보드
- [ ] pg_partman 자동 파티션
- [ ] Phase 2 룰셋 (치매, 미세먼지, 황사)

---

## 12. 정의 (Done)

- 각 슬라이스 "수락 기준" ✅ + 회귀 테스트 그린 + PR 머지
- 단위 테스트 그린
- Smoke Test 통과 (각 슬라이스 문서 하단 `curl` 시나리오)
- 다른 개발자가 README만 보고 `docker compose up` → 환경 기동 가능

**구현 순서·DDL·API 계약은 `issues_v2/*.md`가 ground truth.** 변경 필요하면 슬라이스 문서 먼저 수정, 코드는 그 다음.

---

## 13. 프론트엔드 동기화

| 백엔드 슬라이스 완료 시 | 프론트가 mock → 실 API 교체 | 책임 |
|---|---|---|
| 04 (약장 + 안전판정) | F2 (★ 데모 핵심) | B 수종 |
| 05 (질병금기) | F3 | B 수종 |
| 06·07 (날씨) | F5 | 공동 |
| 08 (응급카드) | F4 | B 수종 |
| 01 (약 마스터) | F2·F4·F6 알약 사진 | A 동환 |

각 슬라이스 PR 머지 시 프론트 개발자에게 Slack/Teams 알림 — **담당자가 직접**.

---

## 14. 막힐 때

| 상황 | 참조 |
|---|---|
| DDL 모르겠음 | 해당 슬라이스의 "DB 적재" 섹션 |
| API 응답 구조 | 해당 슬라이스의 "API 계약" + `api-types/*.ts` |
| 블로커 12개 결정 | `PRD_엄마약_v6.md` §블로커 12개 매핑 |
| 프론트와 충돌 | `HANDOFF_COMMON.md` |
| 전체 흐름 시각화 | `엄마약_API_흐름도_v8.html` (브라우저) |
| Spring Boot 4.x 마이그레이션 가이드 | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes |
| 짝꿍이 막힘 | 페어 프로그래밍 / 페어 PR review |
