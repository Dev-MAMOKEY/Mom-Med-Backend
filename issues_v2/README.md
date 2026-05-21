# 엄마약 — 백엔드 슬라이스 인덱스 v2

PRD v6 + HTML 흐름도 v7(→v8 예정)을 기준으로 9개 수직 슬라이스(tracer-bullet) 재작성. v1 검수에서 발견된 **블로커 12개 + 핵심 minor 이슈** 모두 해결.

- 기준 문서: `PRD_엄마약_v6.md`, `엄마약_API_흐름도_v7.html`
- 범위: **백엔드 + 데이터 적재 + 외부 API + 운영 잡** (프론트엔드 UI는 별도 PRD)
- DB: **PostgreSQL 14+**, 4-schema (`ref` / `app` / `derived` / `logs`)
- LLM: **Gemini 2.5 Flash Lite** (구현 단계에서 변경 가능)

---

## v1 → v2 주요 변경

| 항목 | v1 | v2 |
|---|---|---|
| DB schema | 단일 schema | **4-schema 분리** (ref/app/derived/logs) |
| 슬라이스 수 | 9 | 9 + 응급카드(⑨ 모듈로 격상) |
| patient_conditions 형식 | JSONB or 테이블 모호 | **정규화 테이블 확정** |
| 신규 테이블 | — | **`app.patient_allergies`, `app.device_tokens` 추가** |
| nb_interactions entry_type | slice 03·05 ALTER 분산 | **slice 03 초기 정의에 통합** |
| DUR 매칭 | `LIKE '%X%'` (인덱스 무력화) | **`=` 정확 매칭** + 정규화 강화 |
| 04 의존성 | 00·01만 | **+ 02·03 (SafetyJudge 호출)** |
| 06 의존성 | 04만 | **+ 05 (patient_conditions)** |
| 07 의존성 | 04·06 | **+ 05 (patient_conditions)** |
| 08 의존성 | 00·04만 | **+ 05 (응급카드 snapshot에 conditions 활용)** |
| 응급카드 정렬 | 알레르기·항응고제 데이터 없음 | **patient_allergies + ATC B01A* 정렬 로직** |
| LLM | Claude Sonnet 4 | **Gemini 2.5 Flash Lite** (변경 가능 단서) |

---

## 의존성 그래프 (v2 — 수정됨)

```
                ┌─────────────┐
                │ 00 Foundation│ ← 4-schema migration + 유틸
                └──────┬──────┘
                       │
        ┌──────────────┼──────────────────────┐
        ▼              ▼                      ▼
   ┌─────────┐    ┌─────────┐           ┌───────────┐
   │01 약식별│    │04 부모  │ ◀─┐       │08 응급카드 │
   │  +사진  │    │  +약장  │   │       │           │
   └────┬────┘    └────┬────┘   │       └─────┬─────┘
        │              │        │             │
        ▼              │        │             │
   ┌─────────┐         │        │             │
   │02 DUR 1차│ ───┐   │        │             │
   └─────────┘    │   │        │             │
        │         │   │        │             │
        ▼         │   │        │             │
   ┌─────────┐    │   │        │             │
   │03 NB AI │ ───┼───┘        │             │
   │  2차    │    │            │             │
   └────┬────┘    │            │             │
        │         │            │             │
        ▼         │            │             │
   ┌─────────────┐│            │             │
   │05 질병      │◀────────────┘             │
   │  +환자분류  │                            │
   └────┬────────┘                           │
        │              ◀────────────────────────┘  (08도 05 의존)
        ▼
   ┌─────────────┐
   │06 날씨룰셋  │ ← 04·05 의존
   │  +룩업      │
   └────┬────────┘
        ▼
   ┌─────────────┐
   │07 기상청    │ ← 04·05·06 의존
   │  ETL+푸시   │
   └─────────────┘
```

## 슬라이스 목록 (v2)

| # | 파일 | 제목 | 의존 | 분량 | PRD 모듈 |
|---|---|---|---|---|---|
| 00 | `00-foundation-v2.md` | 4-schema 인프라 + 공유 유틸 | — | 2일 | ⑤ + 인프라 |
| 01 | `01-drug-identity-pill-visual-v2.md` | 약 식별 + 알약 사진 | 00 | 3일 | ①, ⑥ |
| 02 | `02-dur-coadministration-check-v2.md` | DUR 1차 병용금기 (정확 매칭) | 00, 01 | 3일 | ②, ④ MVP |
| 03 | `03-nb-extraction-2layer-safety-v2.md` | NB AI 추출 + 2겹 안전망 | 00, 01, 02 | 4일 | ③, ④ 확장, ⑤ |
| 04 | `04-parent-medication-management-v2.md` | 부모 등록 + 약장 + 알레르기 + 디바이스 토큰 | 00, 01, 02, 03 | 3일 | (인프라) |
| 05 | `05-disease-patient-class-contraindication-v2.md` | 기저질환 + 환자분류 금기 | 00, 02, 03, 04 | 3일 | ③ 확장 |
| 06 | `06-weather-ruleset-lookup-v2.md` | 날씨 룰셋 + 룩업 모듈 | 00, 04, 05 | 2일 | ⑦ 데이터 |
| 07 | `07-weather-etl-daily-push-v2.md` | 기상청 ETL + 매일 푸시 | 00, 04, 05, 06 | 3일 | ⑦ 운영 |
| 08 | `08-emergency-card-v2.md` | 응급카드 (알레르기 + 항응고제 정렬) | 00, 04, 05 | 2일 | ⑨ (신규) |

**총 분량**: 약 25일 (4~5주, 1인 풀타임 기준 / 팀 작업 시 더 짧음)

## 권장 작업 순서

```
Week 1: 00 → 01           (4-schema 인프라 + 첫 사용자 가치)
Week 2: 02 → 03 → 04      (약물 안전 + 부모 등록 컨텍스트)
Week 3: 05 → 06           (질병 + 룰셋)
Week 4: 07 → 08           (운영 잡 + 응급카드)
Week 5: 통합테스트·버그픽스
```

## 환경 변수 (전체 — v2)

| 키 | 발급처 | 사용 슬라이스 |
|---|---|---|
| `MFDS_API_KEY` | data.go.kr (식약처) | 01·02·03·05 |
| `HIRA_API_KEY` | data.go.kr (HIRA) | 02·05·08 |
| `KMA_API_KEY` | data.go.kr (기상청) | 07 |
| `GEMINI_API_KEY` | Google AI Studio | 03·05 (LLM, 구현 시 변경 가능) |
| `DATABASE_URL` | 자체 PostgreSQL 14+ | 전체 |
| `REDIS_URL` | 자체 Redis 7+ | 03·07 |
| `APP_ENCRYPTION_KEY` | secrets manager | 04 (pgcrypto 컬럼 암호화) |

## 회귀 자산 (v2)

| 자산 경로 | 사용 슬라이스 |
|---|---|
| `nb_amlodipine.txt/.json` | 03 (NB 추출 골든) |
| `nb_warfarin.txt/.json` | 03 (NB 추출 골든) |
| `data/_downloads/11983_ex/*.csv` | 02 (DUR CSV) |
| `data/건강보험심사평가원_ATC코드 매핑 목록_20250630.csv` | 01·02 |
| `seed/weather_rules_v0.2.json` | 06·07 |
| `기상청41_단기예보.../격자_위경도(2510).xlsx` | 06 (region_grid 적재) |

## 정의 (모든 슬라이스 공통)

- **Done의 정의**: 수락 기준 모두 ✅ + 회귀 테스트 통과 + PR 머지
- **회귀 테스트는 결정적**: LLM 출력도 결정적 판정 기준(예: "환각 0건", "재현율 100%")으로 평가
- **테이블 prefix 필수**: 모든 DDL·SELECT에 schema prefix (`app.patient_profiles`)
- **외부 API 호출 단위 mock + 통합 실호출 분리**

## 범위 밖 (v2)

별도 PRD/슬라이스로 처리:
- 프론트엔드 UI (자녀 앱 화면·푸시 UI)
- 가족 연결 (자녀-부모 본인인증) — 04에서 device_tokens만 최소 처리
- 처방전 OCR
- 약사 화상 상담
- 결제·구독
- Phase 2 룰셋 확장 (F03 치매, 미세먼지·황사 등)
- 응급 의료진 전용 앱·UI (08은 QR snapshot JSON만)
