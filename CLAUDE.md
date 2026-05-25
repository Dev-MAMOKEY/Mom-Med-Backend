# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**엄마약 (Mom's Med)** — 부모 복약 안전 관리 서비스 백엔드.  
Spring Boot 4.0.6 · Java 21 · PostgreSQL 18 · Redis 7 · Flyway · Gemini 2.5 Flash Lite.

## Commands

```bash
# 빌드
./gradlew build

# 테스트 전체
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "mamokey.mom_med.backend.global.util.DrugNameNormalizerTest"

# 앱 실행 (local profile)
./gradlew bootRun --args='--spring.profiles.active=local'

# DUR CSV 적재 포함 실행
./gradlew bootRun --args='--spring.profiles.active=local --app.etl.dur.enabled=true'

# Docker 인프라 (PostgreSQL + Redis)
docker compose up -d
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Architecture

### 패키지 구조

```
mamokey.mom_med.backend
├── domain/           # 핵심 비즈니스 도메인 (drug, dur, nb, safety)
├── parent/           # 부모 컨텍스트 (profile, medication, allergy, condition, device-token)
├── external/         # 외부 API 클라이언트 (mfds, hira)
├── infra/            # 인프라 (llm/GeminiClient, etl/DurCsvLoader)
└── global/           # 공통 (exception, config, rsdata, audit, util)
```

### 4-Schema DB 설계

Flyway가 4개 스키마를 모두 관리 (`default-schema: app`):

| 스키마 | 용도 |
|--------|------|
| `app`  | 사용자 데이터 (patient_profiles, patient_medications, patient_conditions, patient_allergies 등) |
| `ref`  | 공공 데이터 정적 참조 (drugs_master, dur_* 5개 테이블, disease_master) |
| `derived` | LLM 추출 캐시 (nb_extractions, nb_interactions) |
| `logs` | 감사 로그 (safety_check_log) |

### 슬라이스 빌드 순서 (구현 완료: 00~05, 08)

```
00 인프라 (4-schema, GeminiClient, HallucinationVerifier)
01 약 마스터 + 낱알식별 (MfdsClient → ref.drugs_master)
02 DUR 병용금기 (DurCsvLoader → ref.dur_* → DurRuleEngine)
03 NB 추출 (GeminiClient → NbExtractionService → derived.nb_extractions)
04 부모 약장 + 안전판정 통합 (MedicationService → SafetyJudgeService)
05 기저질환 + 환자분류금기 확장 (ConditionService + SafetyJudgeService 확장)
08 병원·약국·응급카드 (HospitalService · PharmacyService · EmergencyCardService)
```

### 핵심 흐름: 약 추가 시 안전판정

`POST /v1/parents/{parentId}/medications` 호출 시:

1. `DrugIdentifyService.fetchByItemSeq()` — DB 캐시 없으면 MFDS API 자동 캐싱 (`REQUIRES_NEW` TX)
2. `SafetyJudgeService.judge()` — 3겹 안전망:
   - **1차 DUR**: 병용금기 (`ref.dur_combo_contraindications`) → BLOCK이면 즉시 반환
   - **2차 NB AI**: 양방향 drug-drug 상호작용 (`derived.nb_interactions`)
   - **3차 Slice 05**: 연령금기 · 임부금기 · 환자분류금기(KCD) · 알레르기
3. `SafetyCheckLogWriter.record()` — `REQUIRES_NEW` TX로 `logs.safety_check_log`에 독립 커밋 (BLOCK도 기록)
4. BLOCK → `SafetyBlockException` → 409, ALLOW/WARN → 201

### 약물명 정규화

`DrugNameNormalizer.normalize()` — 식약처 표기와 DUR 표기를 맞추는 공통 키.  
`main_ingr_norm` (DrugMaster) ↔ `ingredient_norm` (DUR 테이블) 컬럼 간 `=` 정확 매칭으로만 조회 (LIKE 사용 금지).

### NB 추출 캐시

`NbExtractionService.extract()` 는 `REQUIRES_NEW` TX로 실행 — Gemini API 실패가 외부 TX를 오염시키지 않도록.  
캐시 키: `item_seq + drug_change_date + llm_model + prompt_version`.  
`extractNbSafely()` 에서 모든 예외를 잡아 빈 결과로 graceful degradation.

### DUR CSV ETL

`DurCsvLoadRunner` (`--app.etl.dur.enabled=true` 시만 활성). 실패해도 앱 기동은 계속됨.  
CSV 인코딩: CP949 우선, 실패 시 UTF-8 자동 재시도.  
`ingredient_norm` 컬럼: VARCHAR(500) (V018 마이그레이션 적용).

### Slice 08: 병원·약국·응급카드

**단골 병원/약국** — HIRA XML API 3종으로 데이터 조회 후 `app.parent_hospitals` / `app.parent_pharmacies` 저장:
- `HiraHospitalClient` (sno 11999): 병원 검색 (yadmNm 또는 ykiho)
- `HiraHospitalDetailClient` (sno 12101): 응급실 상세 → `app.hospital_emergency_info` upsert (ykiho 공유 캐시)
- `HiraPharmacyClient` (sno 12100): 약국 검색

**응급카드 자동 갱신**:
- 약장·알레르기·기저질환 변경 TX 커밋 후 `ParentDataChangedEvent` 발행
- `EmergencyCardService.onParentDataChanged()` (`@TransactionalEventListener(AFTER_COMMIT)`)가 `app.emergency_cards.snapshot` 자동 갱신 (qr_token 유지)
- 자녀 명시적 재발급 시에만 qr_token 교체 (`POST /v1/parents/{id}/emergency-card/regenerate`)

**공개 QR 조회** (`GET /em/{token}`, 인증 없음):
- Bucket4j in-memory rate limit: IP 10/min · 토큰 60/hour · 200/day (일일 초과 시 자동 revoke)
- access_count: `@Modifying @Query` JPQL로 원자적 증가
- `Cache-Control: no-store` 헤더 필수

## 응답 구조

일반 응답: `RsData<T>` `{ success, code, message, data }`  
BLOCK 응답: `BlockErrorResponse` `{ error: "block", verdict: {...} }` (HTTP 409, 프론트 합의)

## 트랜잭션 주의사항

- `SafetyCheckLogWriter` · `NbExtractionService.extract()` 는 `REQUIRES_NEW` — 호출자 TX와 완전 독립.
- `catch (Exception)` 으로 외부 장애를 삼키는 코드가 있으면, 해당 메서드가 참여 TX를 가지면 `UnexpectedRollbackException` 이 발생할 수 있음. 반드시 `REQUIRES_NEW` 또는 `TransactionException` 재throw로 해결.

## Flyway 규칙

- 한 번 커밋된 `V` 파일은 절대 수정 금지. 변경은 새 버전 파일 추가.
- 문제 발생 시 모든 스키마 + `flyway_schema_history` 테이블 전체 삭제 후 재시작 (pgAdmin에서 Auto commit ON 필수).
- 현재 최신: V020 (V019: Slice 08 테이블 생성, V020: hospital_emergency_info CHAR→VARCHAR 픽스)

## 외부 API 키 (application-local.yml)

```yaml
external:
  mfds.api-key:    # 식약처
  hira.api-key:    # HIRA (KCD 코드 검증)
  kma.api-key:     # 기상청 (Slice 06~07)
  gemini.api-key:  # Gemini LLM
app:
  encryption-key:  # pgcrypto 마스터키 (device_tokens 암호화)
```
