# 엄마약 기능 5 (날씨×질병 주의 메시지) — 시드 데이터 인덱스

- **수집일**: 2026-05-20
- **목적**: AI(LLM)가 (질병코드 × 기상특보) → 주의 메시지 룰셋을 큐레이션할 때 입력으로 쓸 1차 출처 모음
- **Phase 1 범위**: 폭염·한파 우선 (미세먼지·황사·건조·일교차는 Phase 2)

---

## 폴더 구조

```
seed/
├── heat_wave/   (폭염 4건)
│   ├── 01_e_health_general.md         — 폭염 일반 예방수칙 (e보건소)
│   ├── 02_kdca_노인고혈압.md           — 노인 고혈압 + 폭염 가중 요인 분석
│   ├── 03_kdca_노인당뇨병.md           — 노인 당뇨병 + 폭염 가중 요인 분석
│   └── 04_mohw_어르신_오늘건강.md      — 정부 4단계 강도 체계 참조
└── cold_wave/   (한파 2건)
    ├── 01_kdca_general_winter.md      — 겨울철 한파대비 건강수칙 일반
    └── 02_kdca_card_news_cold.md      — 한랭질환 카드뉴스 (위험군·응급)
```

## 자료별 출처 (모두 정부/공공 1차 출처)

| 파일 | 발행기관 | URL |
|---|---|---|
| heat_wave/01 | 공공보건포털 e보건소 | [link](https://e-health.go.kr/gh/heNws/selectBbsDtlViewInfo.do?menuId=200048&bbsId=U00186&bbsNo=418779) |
| heat_wave/02 | 질병관리청 국가건강정보포털 | [link](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=6698) |
| heat_wave/03 | 질병관리청 국가건강정보포털 | [link](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=5306) |
| heat_wave/04 | 보건복지부 보도자료 | [link](https://www.mohw.go.kr/board.es?mid=a10503000000&bid=0027&list_no=1486077&act=view) |
| cold_wave/01 | 질병관리청 국가건강정보포털 | [link](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=2048) |
| cold_wave/02 | 질병관리청 카드뉴스 | [link](https://www.kdca.go.kr/bbs/kdca/45/216717/artclView.do?layout=unknown) |

## 수집되지 않은 자료 (시도했으나 404·동적 ID·이미지 전용)

| 자료 | 사유 | 대안 |
|---|---|---|
| 2025 폭염대비 건강수칙 리플렛 PDF | 동적 첨부 ID로 404 | 보도자료 본문에서 같은 내용 추출됨 |
| KDCA `contents.es` / `board.es` 페이지 | WebFetch 차단 (KDCA가 봇 차단 추정) | 같은 내용이 health.kdca.go.kr 패턴으로 접근 가능 |
| 한랭질환 카드뉴스 슬라이드 원문 | 이미지 슬라이드라 텍스트 추출 불가 | 페이지 요약본만 확보 (`cold_wave/02`) |

## 강도 단계 설계 (보건복지부 모델 채택)

기상청 영향예보 4단계와 동일:
1. **관심** (낮은 단계)
2. **주의** (중간 단계)
3. **경고** (높은 단계)
4. **위험** (최고 단계)

---

## 부모 페르소나 매칭

엄마약 페르소나(72세, 당뇨·고혈압·관절염·경도인지장애)와 시드 데이터의 직접 적용도:

| 페르소나 속성 | 시드 매칭도 | 활용 시드 |
|---|---|---|
| 65세 이상 | ★★★ | 전 시드에서 65+ 명시 |
| 고혈압 | ★★★ | heat_wave/02 직접, cold_wave/01 직접 |
| 당뇨 | ★★★ | heat_wave/03 직접 |
| 관절염 | ★ | 한파 시 통증 가중 (일반 의학 상식) |
| 경도인지장애 | ★ | 시드엔 없음 — LLM 추론 + 별도 자료 필요 |

→ **부모 페르소나의 핵심 위험인 폭염×고혈압, 폭염×당뇨, 한파×고혈압 케이스는 시드로 충분히 커버됨**

---

## 다음 단계 — RuleSetCurator 프롬프트 설계

1. **입력 형식**:
   - 6개 시드 문서 묶음
   - 대상 (질병코드, 기상특보) 쌍 목록 (예: I10×폭염경보, E11×한파주의보, J45×폭염주의보, ...)
2. **출력 형식 (JSON)**:
   ```json
   {
     "disease_code": "I10",
     "weather_alert": "폭염경보",
     "severity": "경고",
     "message_template": "...",
     "source_citations": ["heat_wave/02#약물치료시주의사항", ...],
     "rationale": "이뇨제 복용 + 발한으로 저나트륨혈증 위험 가중"
   }
   ```
3. **품질 기준**:
   - 모든 entry에 source_citation 1개 이상
   - LLM 일반 지식만으로 만든 entry는 별도 표시 (의료진 검수 우선순위)
   - 환각 검증: source_citation이 시드 문서에 실재하는지 자동 체크
4. **검수 워크플로우**:
   - DraftRuleSet → 자문 의료진 1주 검토 → 최종 weather_rules 테이블 배포

## 추가로 모으면 좋은 시드 (Phase 1 확장 시)

- 대한고혈압학회 진료지침 (계절·운동 권고 섹션)
- 대한당뇨병학회 환자교육자료 (탈수·저혈당)
- 대한노인병학회 노쇠 평가·관리 (취약군 정의)
- 식약처 의약품 허가사항 NB_DOC_DATA 중 "고온 보관"·"탈수 시 주의" 항목 (우리가 이미 자동 추출 가능)
