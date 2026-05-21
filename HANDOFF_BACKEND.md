# 백엔드 개발자 핸드오프 문서

> 엄마약 (Mom's Med) — 2026 보건의료빅데이터·AI 활용 창업경진대회 출품작
> 백엔드 개발자가 받는 첫 문서. 이것만 따라가면 일 시작 가능.

---

## 📦 전달받을 파일 목록 (총 14개 + 1개 폴더)

다음 파일들을 ZIP으로 받거나 폴더 통째로 복사하세요. **모든 경로는 프로젝트 루트 기준**.

### ★ 필수 (개발 시작에 반드시 필요) — 13개

| # | 파일명 | 크기 | 용도 |
|---|---|---|---|
| 1 | `PRD_엄마약_v6.md` | ~35KB | 전체 비전·7기능·12 블로커 매트릭스·DB 4-schema |
| 2 | `엄마약_API_흐름도_v8.html` | ~89KB | 시각화: 4-schema·24 테이블·17 블로커 해결 매트릭스 |
| 3 | `issues_v2/README.md` | ~5KB | **9 슬라이스 빌드 순서·의존성 그래프** (여기서 시작) |
| 4 | `issues_v2/00-foundation-v2.md` | ~10KB | 4-schema 인프라·LLM 클라이언트·환각 검증기 |
| 5 | `issues_v2/01-drug-identity-pill-visual-v2.md` | ~8KB | 식약처 API·약 마스터·낱알식별 |
| 6 | `issues_v2/02-dur-coadministration-check-v2.md` | ~9KB | HIRA DUR 837k행·정확매칭 |
| 7 | `issues_v2/03-nb-extraction-2layer-safety-v2.md` | ~11KB | LLM NB 추출·환각 검증 |
| 8 | `issues_v2/04-parent-medication-management-v2.md` | ~12KB | ★ 약장 CRUD + 안전판정 통합 |
| 9 | `issues_v2/05-disease-patient-class-contraindication-v2.md` | ~11KB | 질병금기·KCD·patient_conditions 정규화 |
| 10 | `issues_v2/06-weather-ruleset-lookup-v2.md` | ~9KB | 날씨 룰셋 32개·격자좌표·룩업 |
| 11 | `issues_v2/07-weather-etl-daily-push-v2.md` | ~10KB | 매일 ETL·푸시·Redis 락 |
| 12 | `issues_v2/08-emergency-card-v2.md` | ~13KB | 응급카드·HIRA 11999/12101/12100 |
| 13 | `api-types/` 폴더 (10개 파일, ~32KB) | — | **프론트가 기대하는 API 응답 구조** (백엔드도 동일 검증 권장) |

### ⭐ 공통 자료
| # | 파일 | 용도 |
|---|---|---|
| 14 | `HANDOFF_COMMON.md` | 프론트와 합의된 사항 — 필독 |

### 🗂️ 데이터 파일 (★ DB 적재용 — 필수)

PostgreSQL에 적재할 원본 데이터. 슬라이스별 매핑:

| # | 파일 / 폴더 | 크기 | 사용 슬라이스 | 비고 |
|---|---|---|---|---|
| 15 | `data/건강보험심사평가원_의약품안전사용서비스(DUR) 의약품 목록_20250601/` | **240MB** | **02** | ★ 5개 CSV (병용금기·연령·임부·노인·노인NSAID) |
| 15a | └─ `의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv` | **231MB** | 02 | ★ 837k행 — 가장 큰 파일 |
| 15b | └─ `의약품안전사용서비스(DUR)_임부금기 품목리스트 2025.6.csv` | 4.8MB | 02·05 | |
| 15c | └─ `의약품안전사용서비스(DUR)_연령금기 품목리스트 2025.6.csv` | 620KB | 02·05 | |
| 15d | └─ `의약품안전사용서비스(DUR)_노인주의 품목리스트 2025.6.csv` | 192KB | 02 | |
| 15e | └─ `의약품안전사용서비스(DUR)_노인주의(해열진통소염제) 품목리스트 2025.6.csv` | 324KB | 02 | |
| 16 | `data/건강보험심사평가원_ATC코드 매핑 목록_20250630.csv` | 2.4MB | 01·02 | gnlNmCd ↔ ATC 매핑 |
| 17 | `기상청41_단기예보 조회서비스_오픈API활용가이드_2510/` 폴더 | ~1MB | 06·07 | API 가이드 + 격자 좌표 xlsx |
| 17a | └─ `...격자_위경도(2510).xlsx` | 680KB | 06 | 26K+ 행정구역 → nx/ny 격자 |
| 17b | └─ `...단기예보 조회서비스_오픈API활용가이드_241128.docx` | 394KB | 06·07 | 기상청 API 사용 가이드 |
| 18 | `seed/` 폴더 | ~120KB | 06 | ★ 룰셋 + 회귀 자산 |
| 18a | └─ `weather_rules_v0.2.json` | 76KB | 06 | ★ 32 룰 (환각 검증 완료) |
| 18b | └─ `heat_wave/` (4개 md) | ~50KB | 06 | 환각 검증 ground truth (KDCA·MOHW·e보건소) |
| 18c | └─ `cold_wave/` (2개 md) | ~20KB | 06 | 환각 검증 ground truth (KDCA) |
| 19 | `data/` HIRA OpenAPI 가이드 PDF 9종 | ~9MB | 02·05·06·08 | API 호출 규약 참조 |
| 20 | `nb_amlodipine.txt` + `nb_amlodipine_extracted.json` | 30KB | 03 회귀 | NB 추출 ground truth (39 entries) |
| 21 | `nb_warfarin.txt` + `nb_warfarin_extracted.json` | 36KB | 03 회귀 | NB 추출 ground truth (46 entries) |

**전체 데이터 합계: 약 250MB**

#### 데이터 적재 가이드

각 슬라이스의 "DB 적재" 섹션 참조. 요약:

- **Slice 02 적재**: 5개 DUR CSV → `ref.dur_*` 5개 테이블 (멱등성 UNIQUE 키)
- **Slice 06 적재**: 
  - `weather_rules_v0.2.json` → `ref.weather_rules` (32행)
  - 기상청 xlsx → `ref.region_grid` (26K+ 행)
- **Slice 01 적재**: 식약처 API 실시간 호출 (CSV 없음) — API 키만 필요
- **Slice 05 적재**: HIRA 12904 (상병마스터) 실시간 호출 + DUR 연령/임부 CSV 재활용

#### ⚠️ 큰 파일 처리 옵션

**병용금기 CSV 231MB**는 전달 방식 결정 필요:

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. ZIP에 포함 | 단일 패키지 | ZIP ~250MB |
| B. Google Drive / Dropbox 링크 | 가벼운 ZIP | 별도 다운로드 |
| C. 백엔드 개발자가 직접 다운로드 | 양쪽 다 가벼움 | 출처 URL 안내 필요 |

> 출처: HIRA 공공데이터포털 (https://www.data.go.kr/data/15095677/fileData.do) — sno 11983 의약품안전사용서비스(DUR) 의약품 목록 (2025.6 기준)

---

## 🚀 첫날 작업 (4시간 코스)

```
[10:00-10:30]  PRD v6 §1~§3 읽기 (개요·기능·블로커)
[10:30-10:45]  엄마약_API_흐름도_v8.html 브라우저로 열어 보기
[10:45-11:15]  HANDOFF_COMMON.md 정독 (★ 프론트와 합의 사항)
[11:15-12:00]  issues_v2/README.md + 의존성 그래프 이해

[점심]

[13:00-14:30]  Slice 00 (foundation) 정독 — DDL·환경변수·LLM 클라이언트
[14:30-16:00]  Slice 00 코딩 시작 — Docker Compose·alembic·4-schema
```

---

## 📚 슬라이스 빌드 순서

```
00 인프라 (4-schema, LLM 클라이언트, 환각 검증기)
 ↓
01 약 마스터 + 알약 이미지 (식약처 15095677·15057639)
 ↓
02 DUR 병용금기 (HIRA 11983, 837k행, 정확매칭 ★)
 ↓
03 NB 추출 (LLM 2겹 안전망, 환각 검증)            ← 01·02 의존
 ↓
04 부모 약장 + 안전판정 통합 (★ 가장 큰 슬라이스)  ← 01·02·03 의존
 ↓
05 질병·환자분류 금기 (KCD 매핑)                   ← 02·03·04 의존
 ↓
06 날씨 룰셋 32개 + 격자좌표 + 룩업              ← 04·05 의존
 ↓
07 매일 ETL + 자체 판정 + 푸시 (Redis 락 2단)    ← 04·05·06 의존
 ↓
08 응급카드 + HIRA 11999/12101/12100             ← 04·05 의존
```

병렬 가능 지점:
- 00 완료 후 → 01·02·05 룰셋 ETL 부분 병렬 가능
- 04 완료 후 → 06·08 병렬 가능
- 07은 06 완료 후

---

## 🔑 환경변수 (`.env`)

```env
MFDS_API_KEY=                    # 식약처 API
HIRA_API_KEY=                    # HIRA API
KMA_API_KEY=                     # 기상청 API
GEMINI_API_KEY=                  # LLM (Gemini 2.5 Flash Lite, 변경 가능)
DATABASE_URL=postgresql://...
REDIS_URL=redis://...
APP_ENCRYPTION_KEY=              # pgcrypto 마스터키
```

7개 모두 Slice 00의 `.env.example`에 정의됨.

---

## ✅ 코딩 시작 체크리스트

- [ ] PostgreSQL 14+ + Redis 7+ 설치 (Docker 권장)
- [ ] 4개 외부 API 키 발급 (식약처·HIRA·기상청·Gemini)
- [ ] Python + FastAPI 또는 TypeScript + NestJS 결정
- [ ] alembic 마이그레이션 셋업
- [ ] CI (GitHub Actions: lint·test·migration dry-run)

---

## 📞 프론트엔드와 동기화 포인트

| 백엔드 슬라이스 완료 | 프론트가 mock → 실 API 교체 |
|---|---|
| 04 (약장 CRUD + 안전판정) | F2 (★ 데모 핵심) |
| 05 (질병금기) | F3 |
| 06·07 (날씨) | F5 |
| 08 (응급카드) | F4 |
| 01 (약 마스터) | F2·F4·F6 알약 사진·약 상세 |

각 슬라이스 PR 머지 시 **프론트 개발자에게 알림** (Slack·Teams).

---

## ⚠️ 별도 PRD 필요한 부분 (MVP 범위 밖)

- 카카오 OAuth · 실제 SMS 발송 ("인증·세션 PRD")
- 카카오톡 알림톡 ("부모 알림 전달 PRD")
- 처방전 OCR 모델 통합 ("OCR PRD")
- 의료진 검수 어드민 ("관리자 PRD")

이것들은 MVP 코드에서 mock·placeholder로 처리됨.

---

## ❓ 막힐 때

1. **DDL 모르겠으면** → 해당 슬라이스 문서의 "DB 적재" 섹션
2. **API 계약 모르겠으면** → 해당 슬라이스 "API 계약" 섹션 + `api-types/` 의 Zod schema
3. **블로커 알고 싶으면** → `엄마약_API_흐름도_v8.html`의 "블로커 해결 매트릭스" (17건)
4. **프론트와 충돌나면** → `HANDOFF_COMMON.md`의 합의 사항 확인

---

## 📋 PR 머지 기준

각 슬라이스의 "수락 기준" 체크리스트 모두 통과:
- 단위 테스트 그린
- Smoke Test 통과
- 다른 개발자가 README 보고 재현 가능

**구현 순서·DDL·API 계약은 슬라이스 문서가 ground truth**. 임의로 바꾸지 말고, 변경 필요하면 PRD/슬라이스 문서 먼저 수정 후 코드.
