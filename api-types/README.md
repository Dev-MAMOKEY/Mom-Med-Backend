# API Types — Zod schemas

엄마약 프론트엔드 ↔ 백엔드 API 계약을 Zod로 명세한 타입 라이브러리.

## 파일 구조

| 파일 | 슬라이스 | 백엔드 정합 |
|---|---|---|
| `common.ts` | 전 슬라이스 | enum + ApiError·SafetyBlockError 클래스 |
| `parent.ts` | F1 | 백엔드 04 (app.patient_profiles) + 인증 API (mock) |
| `medication.ts` | F2 | 백엔드 01 (drugs_master + pill_visuals) + 04 (patient_medications) |
| `safety.ts` | **F2 ★** | 백엔드 02 (DUR) + 03 (NB) + 04 (verdict wrapper) |
| `condition.ts` | F3 | 백엔드 05 (patient_conditions 정규화) + 04 (allergies) + HIRA 12904 |
| `emergency.ts` | F4 | 백엔드 08 (emergency_cards) + HIRA 11999·12101·12100 |
| `weather.ts` | F5 | 백엔드 06 (weather_rules + rule_id) + 07 (ETL) + 기상청 |
| `ocr.ts` | F6 | 별도 OCR PRD (MVP mock) |
| `index.ts` | — | 모든 export 통합 |

## v1.1 검수 반영 사항

### ★ F2 safety.ts — 백엔드 04 v2.1 응답 구조 정확 반영

```ts
// HTTP 409 (BLOCK만)
{ error: 'block', verdict: { decision, evidences } }

// HTTP 201 (ALLOW + WARN 둘 다 — 약은 추가됨)
{ medication_id, item_seq, safety_check: { decision, evidences } }
```

이전 v1에서는 wrapper 없이 `{ decision, conflicts }` 평면 구조였음.
v1.1에서 `verdict` wrapper + `evidences` 이름 정정.

### F5 weather.ts — `rule_id` 필드 추가 (백엔드 06 v2.1 블로커 8번)

### F4 emergency.ts — `qr_url`은 `/em/{token}` 패턴 (백엔드와 합의)

## 사용 예

### 1. mutation 응답 검증
```ts
import { AddMedicationReqSchema, AddMedicationResSchema, SafetyBlockError } from '@/api/types';

const mutation = useMutation({
  mutationFn: async (req) => {
    const parsed = AddMedicationReqSchema.parse(req);
    return apiCall('POST', `/v1/parents/${pid}/medications`, parsed, AddMedicationResSchema);
  },
  onSuccess: (res) => {
    if (res.safety_check.decision === 'WARN') {
      // WARN 모달 표시 — 약은 이미 추가됨
    }
  },
  onError: (err) => {
    if (err instanceof SafetyBlockError) {
      // BLOCK 모달 표시 — 약 추가 안 됨
      console.log(err.payload.verdict.evidences);
    }
  },
});
```

### 2. 항응고제 필터 (F2·F4 공유)
```ts
import { isAnticoagulant } from '@/api/types';

const criticalDrugs = medications.filter(isAnticoagulant);
```

### 3. severity 비교
```ts
import { SeverityKoEnum } from '@/api/types';

const order = { '관심': 1, '주의': 2, '경고': 3, '위험': 4 };
advisories.sort((a, b) => order[b.severity] - order[a.severity]);
```

## F0 코딩 시작 시 작업

1. `mom-med/` Expo 프로젝트 생성
2. `cp -r api-types/* mom-med/api/types/` (또는 동일 폴더 트리에 복사)
3. `pnpm add zod` (이미 F0 의존성 목록에 있음)
4. `tsconfig.json` paths 매핑: `"@/api/types": ["api/types/index"]`
5. import 시작
