# Slice 03 v2 — NB AI 추출 + 2겹 안전망 완성

## Why (사용자 가치)
한국 DUR(Slice 02) 누락분 — **amlodipine+simvastatin, warfarin+aspirin, amlodipine+clarithromycin** 같은 임상적 금기를 **식약처 `NB_DOC_DATA` 원문을 LLM이 구조화 추출**해 회수. 2겹 안전망 완성.

검증된 사실: amlodipine 39 + warfarin 46 entry, 환각 0건, 재현율 100%.

## 의존성
- Slice 00 v2: `HallucinationVerifier`, `call_llm`, `cache_get_or_compute`, `GEMINI_API_KEY`
- Slice 01 v2: `ref.drugs_master.nb_doc_data`
- Slice 02 v2: `SafetyJudge` MVP (이 슬라이스에서 확장)

## v1 → v2 변경
- **LLM 모델**: Claude Sonnet 4 → **Gemini 2.5 Flash Lite** (구현 단계에서 변경 가능)
- `derived.nb_interactions`에 **`entry_type` 초기 정의 통합** (slice 05의 ALTER 제거)
- 약물군 → ATC prefix 매핑 사전 명시 (자몽 같은 식품 포함)
- `risk_level` enum 매핑 표 명시
- schema prefix (`derived.*`)

## 외부 API 호출 명세

### API 1 — Gemini 2.5 Flash Lite (LLM 추출)

⚠️ **구현 단계에서 모델 변경 가능**: Claude Sonnet 4, GPT-4o 등으로 교체 시 `utils/llm_client`만 수정.

- **endpoint**: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent`
- **method**: POST · JSON
- **인증**: `x-goog-api-key: ${GEMINI_API_KEY}` 헤더

**요청 body**:
```json
{
  "contents": [{"parts": [{"text": "<PROMPT_TEMPLATE 채워진 본문>"}]}],
  "generationConfig": {
    "responseMimeType": "application/json",
    "maxOutputTokens": 8192,
    "temperature": 0.1
  }
}
```

**curl 예시**:
```bash
curl -s -X POST \
  -H "x-goog-api-key: ${GEMINI_API_KEY}" \
  -H "Content-Type: application/json" \
  -d @prompt.json \
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
```

**응답 (Google Generative Language API)**:
```json
{
  "candidates": [{
    "content": {"parts": [{"text": "{\"drug\":\"암로디핀\",\"interactions\":[...]}"}]}
  }],
  "usageMetadata": {
    "promptTokenCount": 3500,
    "candidatesTokenCount": 6200,
    "totalTokenCount": 9700
  }
}
```

응답 파싱: `response.candidates[0].content.parts[0].text` → JSON.loads

### 프롬프트 템플릿 (검증된 v3)

```text
You are extracting drug-drug and drug-food interactions from a Korean drug 
label's 사용상의주의사항(NB_DOC_DATA) text.

CONTEXT
- Drug: {ITEM_NAME}
- 주성분: {MAIN_INGR_ENG}
- ATC: {ATC_CODE}

INPUT
<text>
{NB_DOC_DATA 정제 평문}
</text>

TASK: 이 텍스트에서 "이 약과 다른 약물/식품 간 상호작용"으로 언급된 모든 
약물·식품·약물군을 추출해, 아래 JSON 스키마에 맞춰 반환.

JSON SCHEMA:
{
  "drug": "<주성분 한글명>",
  "interactions": [
    {
      "partner_drug_ko": "<한글 약물/식품/약물군>",
      "partner_drug_en": "<영문, 없으면 null>",
      "risk_level": "<동시투여피해야함 | 권장하지않음 | 주의 | 정보만>",
      "reason_summary": "<1문장 한국어>",
      "source_quote": "<원문 byte-for-byte 발췌 50~200자>"
    }
  ]
}

RULES:
1. source_quote는 본문에 있는 그대로 (수정·요약 금지)
2. 약물군(NSAIDs, CYP3A4 저해제 등)도 별개 entry
3. 식품(자몽 등) 포함
4. 자기 자신 제외
5. 출력은 strict JSON, 첫 글자 '{'.
```

**risk_level enum 매핑 표** (Gemini가 분류 → DB·SafetyJudge가 해석):

| 본문 표현 | risk_level | SafetyJudge 통합 시 |
|---|---|---|
| "투여하지 말 것", "동시투여는 피해야 한다", "금기" | `동시투여피해야함` | BLOCK |
| "병용투여를 권장하지 않는다" | `권장하지않음` | WARN |
| "주의깊게 관찰", "주의하여 투여", "위험이 증가" | `주의` | INFO |
| "영향이 없었다", 단순 정보 | `정보만` | (UI에는 표시 안 함, 로그만) |

### 비용·성능 추정 (Gemini 2.5 Flash Lite 기준)

- 입력: NB 평문 5,000~10,000자 ≈ 3,000~6,000 토큰
- 출력: 30~50 entries × ~200 토큰 ≈ 6,000~10,000 토큰
- Gemini 2.5 Flash Lite 가격 (2025년 기준 추정): **약물당 $0.001~0.005** (저렴)
- 캐싱으로 약물당 1회만 호출

## DB 적재

### Table: `derived.nb_extractions`

```sql
CREATE TABLE derived.nb_extractions (
    id                      BIGSERIAL PRIMARY KEY,
    item_seq                VARCHAR(20) NOT NULL REFERENCES ref.drugs_master(item_seq) ON DELETE CASCADE,
    drug_change_date        DATE,                          -- NULL 허용
    extraction_json         JSONB NOT NULL,                -- 전체 LLM 응답
    verified                BOOLEAN NOT NULL,              -- HallucinationVerifier 통과 여부
    verification_summary    JSONB,                         -- { total, exact, normalized, fuzzy, hallucinated }
    llm_model               VARCHAR(50) NOT NULL,          -- 'gemini-2.5-flash-lite' (변경 가능)
    prompt_version          VARCHAR(20) NOT NULL,
    token_input             INT,
    token_output            INT,
    cost_usd                NUMERIC(10,6),
    extracted_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(item_seq, drug_change_date, llm_model, prompt_version)
);
CREATE INDEX idx_nb_ext_item_seq ON derived.nb_extractions(item_seq);
CREATE INDEX idx_nb_ext_verified ON derived.nb_extractions(verified) WHERE verified = true;
```

### Table: `derived.nb_interactions` (★ v2: entry_type 초기 통합)

```sql
CREATE TABLE derived.nb_interactions (
    id                      BIGSERIAL PRIMARY KEY,
    extraction_id           BIGINT NOT NULL REFERENCES derived.nb_extractions(id) ON DELETE CASCADE,
    item_seq                VARCHAR(20) NOT NULL,           -- 비정규화 (빠른 조회)
    drug_name               VARCHAR(200) NOT NULL,
    
    -- ★ v2: entry_type을 처음부터 정의 (slice 05의 ALTER 제거)
    entry_type              VARCHAR(20) NOT NULL CHECK (entry_type IN ('drug_drug','patient_class')),
    
    -- drug_drug 전용 필드
    partner_drug_ko         VARCHAR(300),
    partner_drug_norm       VARCHAR(200),                   -- ★ 매칭 키
    partner_drug_en         VARCHAR(200),
    is_drug_group           BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- patient_class 전용 필드 (slice 05에서 채움)
    patient_class_text      VARCHAR(300),
    patient_class_kcd       VARCHAR(50),
    
    -- 공통
    risk_level              VARCHAR(30) NOT NULL,
    reason_summary          TEXT,
    source_quote            TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CHECK (
        (entry_type = 'drug_drug' AND partner_drug_ko IS NOT NULL) OR
        (entry_type = 'patient_class' AND patient_class_text IS NOT NULL)
    )
);

-- 부분 인덱스로 두 유형 분리
CREATE INDEX idx_nbi_drug_partner 
    ON derived.nb_interactions(item_seq, partner_drug_norm) 
    WHERE entry_type = 'drug_drug';
CREATE INDEX idx_nbi_patient_class 
    ON derived.nb_interactions(item_seq, patient_class_kcd) 
    WHERE entry_type = 'patient_class';
CREATE INDEX idx_nbi_partner_norm
    ON derived.nb_interactions(partner_drug_norm)
    WHERE entry_type = 'drug_drug';
```

## 약물군 → ATC prefix 매핑 사전 (의사코드)

```python
# 슬라이스 03의 SafetyJudge에서 약물군 → 특정 약 확장 매칭 시 사용
GROUP_TO_ATC_PREFIX = {
    "CYP3A4 저해제": ["J02AC", "J01FA09"],   # itraconazole·ketoconazole·clarithromycin
    "CYP3A4 유도제": ["J04AB02", "N03AF01"], # rifampicin·carbamazepine
    "비스테로이드성 소염제": ["M01A"],         # NSAID
    "마크로라이드계 항생제": ["J01FA"],
    "아졸계 항진균제": ["J02AC"],
    "mTOR 억제제": ["L04AA10", "L04AA18"],   # sirolimus·everolimus
    "베타차단제": ["C07"],
    "이뇨제": ["C03"],
    "항응고제": ["B01A"],
    "항혈소판제": ["B01AC"],
    # 식품·기타
    "자몽": [],                              # 식품 — ATC 없음, 별도 처리
    "알코올": [],
    "비타민 K": [],
}

def is_drug_group_name(partner_ko: str) -> bool:
    return partner_ko in GROUP_TO_ATC_PREFIX
```

## 흐름 (의사코드)

```python
def extract_nb(item_seq: str) -> ExtractionResult:
    drug = db.fetch_one("ref.drugs_master", item_seq=item_seq)
    
    # 1) 캐시 확인 (drug_change_date + llm_model + prompt_version)
    cached = db.fetch_one("derived.nb_extractions",
        item_seq=item_seq, drug_change_date=drug.source_change_date,
        llm_model="gemini-2.5-flash-lite", prompt_version="v1"
    )
    if cached and cached.verified:
        return ExtractionResult(from_cache=True, **cached.dict())
    
    # 2) NB_DOC_DATA 전처리 (CDATA·태그·엔티티 제거)
    nb_clean = clean_nb_doc_data(drug.nb_doc_data)
    
    # 3) LLM 호출
    response = call_llm(
        prompt=PROMPT_TEMPLATE.format(
            ITEM_NAME=drug.item_name,
            MAIN_INGR_ENG=drug.main_ingr_en,
            ATC_CODE=drug.atc_code,
            NB_DOC_DATA=nb_clean
        ),
        model="gemini-2.5-flash-lite",
        json_mode=True,
        prompt_version="v1"
    )
    extraction = json.loads(response.text)
    
    # 4) 환각 검증 (Slice 00의 verify_quotes)
    verify_result = verify_quotes(extraction, source_files={item_seq: nb_clean})
    if verify_result.hallucinated > 0:
        log.warning("환각 발견", item_seq=item_seq, failures=verify_result.failures)
        if not retry_once_called:
            return extract_nb(item_seq)  # 1회 재시도
        return ExtractionResult(verified=False)
    
    # 5) 저장
    ext_row = db.insert("derived.nb_extractions", item_seq=item_seq, 
        drug_change_date=drug.source_change_date,
        extraction_json=extraction, verified=True,
        verification_summary=verify_result.dict(),
        llm_model=response.model, prompt_version=response.prompt_version,
        token_input=response.input_tokens, token_output=response.output_tokens,
        cost_usd=response.cost_usd
    )
    
    for interaction in extraction['interactions']:
        partner_ko = interaction['partner_drug_ko']
        db.insert("derived.nb_interactions",
            extraction_id=ext_row.id,
            item_seq=item_seq,
            drug_name=extraction['drug'],
            entry_type='drug_drug',                            # 이 슬라이스는 drug_drug만
            partner_drug_ko=partner_ko,
            partner_drug_norm=normalize_drug_name(partner_ko),
            partner_drug_en=interaction.get('partner_drug_en'),
            risk_level=interaction['risk_level'],
            reason_summary=interaction['reason_summary'],
            source_quote=interaction['source_quote'],
            is_drug_group=is_drug_group_name(partner_ko),
        )
    
    return ExtractionResult(verified=True, count=len(extraction['interactions']))
```

## SafetyJudge 확장 (PRD 모듈 ④ 완성)

```python
def judge_full(parent: PatientContext, current_drugs: list[DrugIdentity], 
               new_drug: DrugIdentity) -> SafetyVerdict:
    evidences = []
    
    # 1차: DUR (Slice 02)
    evidences.extend(judge_mvp_inner(parent, current_drugs, new_drug))
    
    # 2차: NB 추출 (이 슬라이스)
    # 새 약의 NB → current_drugs와 매칭?
    extract_nb(new_drug.item_seq)  # 캐시 우선
    nb_rows = db.execute("""
        SELECT * FROM derived.nb_interactions
        WHERE item_seq = :item AND entry_type = 'drug_drug'
    """, {"item": new_drug.item_seq})
    
    for interaction in nb_rows:
        for existing in current_drugs:
            if matches_nb_partner(interaction, existing):
                evidences.append(Evidence(
                    source="NB",
                    risk_level=interaction.risk_level,
                    partner_drug=interaction.partner_drug_ko,
                    reason=interaction.reason_summary,
                    quote=interaction.source_quote,
                    item_seq_source=new_drug.item_seq,
                ))
    
    # 역방향: 기존 약의 NB → new_drug와 매칭?
    for existing in current_drugs:
        extract_nb(existing.item_seq)  # 캐시 우선
        nb_rows_existing = db.execute("""
            SELECT * FROM derived.nb_interactions
            WHERE item_seq = :item AND entry_type = 'drug_drug'
        """, {"item": existing.item_seq})
        for interaction in nb_rows_existing:
            if matches_nb_partner(interaction, new_drug):
                evidences.append(Evidence(source="NB", ...))
    
    # 통합 판정
    if any(e.risk_level in ("병용금기", "동시투여피해야함") for e in evidences):
        decision = "BLOCK"
    elif any(e.risk_level in ("노인주의", "권장하지않음") for e in evidences):
        decision = "WARN"
    elif any(e.risk_level == "주의" for e in evidences):
        decision = "INFO"
    else:
        decision = "ALLOW"
    
    evidences = dedupe_evidences(evidences)
    return SafetyVerdict(decision=decision, evidences=evidences)


def matches_nb_partner(interaction, drug: DrugIdentity) -> bool:
    if interaction.is_drug_group:
        # 약물군 → ATC prefix 매핑 활용
        prefixes = GROUP_TO_ATC_PREFIX.get(interaction.partner_drug_norm, [])
        return any(drug.atc_code.startswith(p) for p in prefixes if drug.atc_code)
    # 단일 약물 — 정규화 base name 비교
    return interaction.partner_drug_norm == drug.main_ingr_norm
```

## API 계약

```
POST /v1/safety/check  (Slice 02 확장)
  
  Response 200 (확장 — source: "NB" 추가):
  {
    "decision": "WARN",
    "evidences": [
      {
        "source": "NB",
        "risk_level": "주의",
        "partner_drug": "심바스타틴",
        "reason": "심바스타틴 노출 77% 증가, 1일 최대 20mg 제한",
        "quote": "암로디핀 10 mg과 심바스타틴 80 mg의 다회용량...",
        "item_seq_source": "200610660"
      }
    ]
  }
  
  Response 409 (BLOCK)

GET /v1/drugs/{item_seq}/contraindications  (신규)
  Response 200:
  {
    "item_seq": "200610660",
    "drug": "암로디핀",
    "interactions": [...],
    "verified": true,
    "extracted_at": "...",
    "llm_model": "gemini-2.5-flash-lite"
  }
```

## 수락 기준

- [ ] `derived.nb_extractions`·`derived.nb_interactions` 테이블 + 인덱스 + CHECK 제약 생성
- [ ] 노바스크정5(item_seq=200610660) → 39 entry, 환각 0건
- [ ] 쿠파린정5(item_seq=200502107, warfarin) → 46 entry, 환각 0건
- [ ] ground-truth 7개 (심바스타틴·이트라코나졸·케토코나졸·시클로스포린·클래리트로마이신·단트롤렌·자몽) 모두 잡힘
- [ ] **노바스크 + 심바스타틴 회귀**: DUR 0건이지만 NB로 WARN 발생 (Slice 02 음성대조의 회수 검증)
- [ ] 환각 검증 실패 시 사용자 노출 안 됨
- [ ] 동일 약 두 번째 호출 시 LLM 호출 0 (DB 캐시 hit)
- [ ] `cost_usd` 메트릭 노출 (`/metrics`)
- [ ] 약물군 매칭: NB에 "CYP3A4 저해제" entry → ATC `J02AC*` 약과 매칭

## 회귀 자산 (이미 보유)
- `nb_amlodipine.txt` + `nb_amlodipine_extracted.json`
- `nb_warfarin.txt` + `nb_warfarin_extracted.json`

검증: `extract_nb()` 결과를 골든과 deep-compare. 분포 차이 < 10%.

## 환경변수
- `GEMINI_API_KEY` (LLM, 구현 시 변경 가능)
- `MFDS_API_KEY` (Slice 01 재호출 시)

## 범위 밖
- 환자분류 금기 추출 (Slice 05 — `entry_type='patient_class'` 채움)
- 약물군 매핑 사전 Phase 2 확장
- 어투 변환 LLM
- `explanation_for_user` 자녀 친화 메시지 생성

## Smoke Test
```bash
# NB 추출 트리거
curl -X POST http://localhost:8000/v1/drugs/200610660/extract-nb | jq .

# DB 확인
psql $DATABASE_URL -c "SELECT count(*) FROM derived.nb_interactions WHERE item_seq='200610660' AND entry_type='drug_drug';"
# 기대: 39

# safety/check 시나리오
curl -X POST http://localhost:8000/v1/safety/check \
  -d '{"parent_id":"t","age":72,"current_drugs":["200610660"],"new_drug":"<simvastatin>"}'
# 기대: HTTP 200 + decision="WARN" + evidence source="NB"
```
