# 백엔드 ↔ 프론트엔드 공통 합의 사항

> 두 팀이 모두 알아야 할 약속. 충돌 시 이 문서가 ground truth.

---

## 📜 핵심 합의 사항 (★ 필독)

### 1. 약 추가 응답 구조 (★ 가장 중요)

백엔드 04 v2.1의 `POST /v1/parents/{id}/medications` 응답:

**Case A — BLOCK (HTTP 409)** — 약 추가 안 됨
```json
{
  "error": "block",
  "verdict": {
    "decision": "BLOCK",
    "evidences": [
      {
        "source": "DUR",
        "severity": "high",
        "message": "출혈 위험...",
        "conflicting_drug": { ... },
        "citation": "...",
        "citation_source": "..."
      }
    ]
  }
}
```

**Case B — ALLOW 또는 WARN (HTTP 201)** — 약 이미 추가됨
```json
{
  "medication_id": "uuid",
  "item_seq": "200610660",
  "safety_check": {
    "decision": "ALLOW" | "WARN",
    "evidences": [ ... ]
  }
}
```

> **백엔드**: 이 응답 구조 그대로 보내야 함.  
> **프론트**: `api-types/safety.ts`의 `AddMedicationResSchema` / `BlockErrorBodySchema` 그대로 사용.

### 2. 응급카드 QR URL 패턴

- **경로**: `/em/{token}` (예: `https://app.엄마약.com/em/abc123`)
- 백엔드 PRD v6의 `/c/{token}`는 **`/em/`로 변경 합의**
- 프론트는 Expo Router의 `app/em/[token].tsx`로 라우팅

### 3. patient_conditions는 정규화 테이블

- ❌ JSONB 배열로 저장 (v1 옵션) — **거부됨**
- ✅ `app.patient_conditions` 정규화 테이블 (백엔드 05 v2.1)
- 프론트는 `ConditionSchema` 배열로 받음 (`api-types/condition.ts`)

### 4. delivery_status enum (백엔드 07)

```sql
CHECK (delivery_status IN (
  'sent', 'failed', 'simulated', 
  'skipped_fatigue',     -- 어제 보내서 skip
  'skipped_review'       -- ★ v2.1 추가: 검수 미통과 skip
))
```

### 5. weather advisory 응답 키

- **백엔드 06과 07 모두 `active_alerts` 사용** (배열)
- 백엔드 06 PRD에 남아있는 `weather_alerts_today` 표기는 `active_alerts`로 통일

### 6. 항응고제 필터 로직 (양쪽 동일)

```ts
isAnticoagulant(m) === m.atc_code?.startsWith('B01A')
```
- 백엔드 08의 응급카드 critical_drugs 생성 시
- 프론트 F2 약장 표시 + F4 응급카드 강조 시
- 동일 로직 (`api-types/safety.ts`의 헬퍼 export)

### 7. severity 색상·enum

- **약 안전판정** (백엔드 02·03·04): `high` / `medium` / `low` (영문)
- **날씨 알림** (백엔드 06·07): `관심` / `주의` / `경고` / `위험` (한글)
- **알레르기** (백엔드 04): `mild` / `moderate` / `severe`

혼동하지 말 것. 각각 다른 도메인의 enum.

---

## 🔄 협업 워크플로우

### 백엔드 슬라이스 ↔ 프론트 슬라이스 동기화

```
[Week 1-2]
  BE: Slice 00 (인프라) + 01 (약 마스터)
  FE: F0 (Expo 셋업) + F1 (역할 선택, mock)
       ↓
       FE는 mock으로 작업 — 백엔드 안 기다림

[Week 3-4] ★ 만나는 지점
  BE: Slice 02·03·04 (DUR + NB + 약장 CRUD)
  FE: F2 코딩 (★ 데모 핵심)
       ↓
       BE 04 완료 시 FE는 EXPO_PUBLIC_USE_MOCK=false 토글로 실 API 검증

[Week 5-6]
  BE: 05 (질병) + 06 (날씨) + 07 (ETL) + 08 (응급카드)
  FE: F3 + F4 + F5 (Mock 진행)
       ↓
       각 BE 슬라이스 PR 머지 시 Slack 알림 → FE가 점진 교체

[Week 7]
  통합 테스트 + Vercel 데모 URL + 시연 영상 1분
```

### Mock → 실 API 교체 방법

프론트에서:
```bash
# .env.demo (개발 중)
EXPO_PUBLIC_USE_MOCK=true

# .env.development (실 백엔드 붙일 때)
EXPO_PUBLIC_USE_MOCK=false
EXPO_PUBLIC_API_BASE_URL=http://localhost:8000
```

`api/client.ts`의 `apiCall` 함수가 USE_MOCK 플래그로 분기. **코드 변경 없음**, 환경변수만.

---

## 📋 백엔드 슬라이스 ↔ 프론트 슬라이스 매트릭스

```
백엔드 →                  F0  F1  F2  F3  F4  F5  F6
─────────────────────────────────────────────────────
00 인프라                  ◐                          
01 약 마스터                       ●           ●  ●
02 DUR                             ●                  
03 NB 추출                         ●                  
04 부모 약장                  ●   ●           ●      
05 질병금기                            ●              
06 날씨 룰셋                                    ●      
07 ETL 푸시                                     ●      
08 응급카드                                ●          
별도 OCR PRD                                        ●  

● = 이 백엔드 슬라이스의 API를 호출
◐ = 인프라 정렬만
```

---

## ⚠️ MVP 범위 밖 (별도 PRD 필요)

이 부분은 **양쪽 다 mock·placeholder**로 처리하고, 실제 구현은 별도 PRD로 분리:

| 항목 | 백엔드 영향 | 프론트 영향 | 별도 PRD |
|---|---|---|---|
| 카카오 OAuth | 인증 API 미구현 | 데모 모드 자동 진입 | "인증·세션 PRD" |
| 부모 SMS 인증 | API 미구현 | "0000" mock 통과 | "인증·세션 PRD" |
| 카카오톡 알림톡 | 미구현 | UI 시뮬만 | "부모 알림 전달 PRD" |
| 처방전 OCR 모델 | 미구현 | mock fixture 4개 약 | "OCR PRD" |
| 의료진 검수 어드민 | 권한 시스템 부재 | UI 없음 | "관리자 PRD" |
| 푸시 (FCM/APNs) | device_tokens는 04에 정의됨 | UI 트리거만 | "푸시 인프라 PRD" |

---

## 🗂️ 공유 자산 위치

| 자산 | 위치 | 양쪽 공유 방법 |
|---|---|---|
| **API Zod schema** | `api-types/*.ts` | FE는 import, BE는 같은 스키마 mirror |
| **디자인 토큰** | `design-tokens.json` | FE 전용 (BE 무관) |
| **PRD (전체)** | `PRD_엄마약_v6.md` | 양쪽 시스템 컨텍스트 |
| **PRD (프론트)** | `PRD_엄마약_FRONTEND_v1.md` | FE 청사진 |
| **백엔드 슬라이스** | `issues_v2/*.md` (9개) | BE 구현 디테일 |
| **프론트 슬라이스** | `frontend_slices/*.md` (7개) | FE 구현 디테일 |
| **API 흐름도** | `엄마약_API_흐름도_v8.html` | 양쪽 전체 그림 |
| **화면 목업** | `엄마약_프론트_목업_v2.html` | FE 시각 참조 |

---

## 📞 분쟁 시 우선순위

| 충돌 상황 | Ground Truth |
|---|---|
| API 응답 구조 의견 차이 | `api-types/*.ts` Zod schema |
| DB 테이블 구조 의견 차이 | `issues_v2/*.md` 의 "DB 적재" 섹션 |
| 화면 디자인 의견 차이 | `엄마약_프론트_목업_v2.html` |
| 기능 범위 의견 차이 | `PRD_엄마약_v6.md` |
| 두 팀 합의 필요 시 | **이 문서 (HANDOFF_COMMON.md)** |

문서를 바꾸는 것이 코드를 바꾸는 것보다 우선. 즉, 코드를 바꾸기 전에 위 문서들을 먼저 갱신.

---

## 🎯 공모전 데모 시연 시나리오 (양쪽 합의)

이 시나리오가 Vercel URL에서 동작해야 함:

```
1. [FE] 시작 화면 → "자녀로 들어가기"
2. [FE] 부모 목록 (mockParents) → 어머니 카드 탭
3. [FE → BE 04] 약장 4개 약 표시 + 항응고제 빨간 테두리
4. [FE] FAB → 약 추가 → 아스피린 검색
5. [BE 04 → FE] 409 BLOCK + verdict.evidences (DUR + NB)
6. [FE] 풀스크린 빨간 모달 + 식약처 원문 인용
7. [FE] 더보기 → 응급카드 (BE 08)
8. [FE] QR 표시 + P1 알레르기 + P2 항응고제
9. [FE] 외부 URL 새 탭 → 큰 글씨 응급의료진용 페이지
10. [FE] 데모 도구 → 폭염경보 시뮬 (F5)
11. [FE] 위험 그라데이션 모달 + KDCA 인용 (BE 06)
12. [FE] /role-select → "내 약장" → 부모 큰 글씨 홈
```

각 단계는 슬라이스 문서의 "Smoke Test" / "사용자 흐름"에 동일하게 명시.

---

## 📅 우선순위 정렬 (양쪽 동시 시작)

| Week | 백엔드 | 프론트엔드 |
|---|---|---|
| 1 | Slice 00 인프라 | F0 Expo 셋업 |
| 2 | Slice 01·02 | F1 + F2 mock |
| 3 | Slice 03·04 ★ | F2 실 API 교체 시작 |
| 4 | Slice 05 | F3 |
| 5 | Slice 06 | F4 |
| 6 | Slice 07·08 | F5 |
| 7 | 통합·튜닝 | F6 + 시연 영상 |

병렬 가능. 양쪽 슬라이스 1주에 1~2개씩.

---

## ✋ 변경 절차

이 문서의 합의 사항을 변경하려면:
1. 양 팀 합의 → 이 문서 갱신 (PR)
2. 영향받는 슬라이스 문서 (BE·FE) 동시 갱신
3. `api-types/`의 Zod schema도 갱신 (해당 시)
4. 코드 변경은 그 다음

**문서 → 코드 순서**가 핵심. 코드만 바꾸지 말 것.
