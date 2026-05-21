# Slice 01 v2 — 약 식별 + 알약 사진

## Why (사용자 가치)
자녀가 약 이름을 입력하면 시스템이 **명확한 식별자(ITEM_SEQ)로 정규화**하고, **실제 알약 사진·외형·각인 정보**를 표시. 어머니 약장의 실물과 시각 대조 가능. 모든 약물 안전 검증의 진입점.

## 의존성
- Slice 00 v2: 4-schema 인프라, `normalize_drug_name`, `cache_get_or_compute`, audit 트리거

## v1 → v2 변경
- DDL에 **schema prefix** (`ref.drugs_master`, `ref.pill_visuals`)
- `source_change_date NULL fallback` 정책 명시
- 정확 매칭 1행 선택 의사코드 추가 + 동명이품 분기 명시
- API 호출 → DB 캐시 무효화 규약 정합성 보강

## 외부 API 호출 명세

### API 1 — 식약처 의약품 제품 허가정보 [목록]
- **sno**: 15095677
- **endpoint**: `https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07`
- **method**: GET · JSON

| 파라미터 | 필수 | 값 출처 | 예시 |
|---|---|---|---|
| `serviceKey` | ✅ | env.MFDS_API_KEY | f204aab... |
| `pageNo` | ✅ | 1 | 1 |
| `numOfRows` | ✅ | 10 | 10 |
| `type` | ✅ | json | json |
| `item_name` | ✅ | 사용자 입력 | "타이레놀" |

```bash
curl -s "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07?serviceKey=${MFDS_API_KEY}&pageNo=1&numOfRows=10&type=json&item_name=%ED%83%80%EC%9D%B4%EB%A0%88%EB%86%80"
```

**응답 샘플 (타이레놀 검색, 1행)**:
```json
{
  "body": {
    "totalCount": 4,
    "items": [{
      "ITEM_SEQ": "202106092",
      "ITEM_NAME": "타이레놀정500밀리그람(아세트아미노펜)",
      "ENTP_NAME": "켄뷰코리아판매유한회사",
      "ITEM_PERMIT_DATE": "20210823",
      "SPCLTY_PBLC": "일반의약품",
      "PRDUCT_TYPE": "[01140]해열.진통.소염제",
      "ITEM_INGR_NAME": "Acetaminophen",
      "BIG_PRDT_IMG_URL": "https://nedrug.mfds.go.kr/.../1OKRXo9l4D5",
      "EDI_CODE": null,
      "BIZRNO": "1068649891"
    }]
  }
}
```

**사용할 필드**:

| 필드 | 사용처 |
|---|---|
| `ITEM_SEQ` | ★ `ref.drugs_master.item_seq` PK |
| `ITEM_NAME` | `ref.drugs_master.item_name` |
| `ENTP_NAME` | `ref.drugs_master.entp_name` |
| `SPCLTY_PBLC` | `ref.drugs_master.specialty_type` |
| `EDI_CODE` | `ref.drugs_master.edi_code` (처방약만) |

**호출 후 처리**:
1. `totalCount == 0` → 404 응답
2. `totalCount > 1` → 동명이품 → 사용자에게 candidates 반환
3. `totalCount == 1` → 자동 선택, 다음 API 호출

### API 2 — 식약처 의약품 제품 허가정보 [상세]
- **endpoint**: `https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnDtlInq06`

| 파라미터 | 필수 | 값 출처 |
|---|---|---|
| `serviceKey` | ✅ | env.MFDS_API_KEY |
| `pageNo` | ✅ | 1 |
| `numOfRows` | ✅ | 1 |
| `type` | ✅ | json |
| `item_seq` | ✅ | **API 1.ITEM_SEQ** |

```bash
curl -s "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnDtlInq06?serviceKey=${MFDS_API_KEY}&pageNo=1&numOfRows=1&type=json&item_seq=202106092"
```

**응답 샘플 (발췌)**:
```json
{
  "body": {
    "items": [{
      "ITEM_SEQ": "202106092",
      "CHART": "흰색의 장방형 필름코팅정제",
      "MATERIAL_NAME": "총량 : 1정615.04밀리그램|성분명 : 아세트아미노펜|분량 : 500|...",
      "MAIN_INGR_ENG": "Acetaminophen",
      "ATC_CODE": "N02BE01",
      "EDI_CODE": null,
      "NB_DOC_DATA": "<DOC>...사용상의주의사항...</DOC>",
      "CHANGE_DATE": "20260408"
    }]
  }
}
```

**사용할 필드**:

| 필드 | 사용처 |
|---|---|
| `MAIN_INGR_ENG` | ★ `ref.drugs_master.main_ingr_en` + `normalize_drug_name()` → `main_ingr_norm` |
| `ATC_CODE` | `ref.drugs_master.atc_code` (일반약 fallback 조인 키) |
| `EDI_CODE` | `ref.drugs_master.edi_code` |
| `CHART` | `ref.drugs_master.chart_text` |
| `NB_DOC_DATA` | **이 슬라이스: 저장만**. Slice 03 LLM 입력 |
| `CHANGE_DATE` | ★ `ref.drugs_master.source_change_date` 캐시 무효화 키 |

### API 3 — 식약처 의약품 낱알식별 정보
- **sno**: 15057639
- **endpoint**: `https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03`

| 파라미터 | 필수 | 값 출처 |
|---|---|---|
| `serviceKey` | ✅ | env.MFDS_API_KEY |
| `pageNo` | ✅ | 1 |
| `numOfRows` | ✅ | 100 |
| `type` | ✅ | json |
| `item_name` | ✅ | **API 1.ITEM_NAME** (item_seq 직접 검색은 60초+로 느림. 실측 확인) |

⚠️ 운영 권장: `item_name` 검색 후 응답에서 `ITEM_SEQ`로 필터링.

```bash
curl -s "https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03?serviceKey=${MFDS_API_KEY}&pageNo=1&numOfRows=100&type=json&item_name=%ED%83%80%EC%9D%B4%EB%A0%88%EB%86%80%EC%A0%95500%EB%B0%80%EB%A6%AC%EA%B7%B8%EB%9E%8C..."
```

**응답 샘플 (타이레놀500)**:
```json
{
  "body": {
    "items": [{
      "ITEM_SEQ": "202106092",
      "ITEM_IMAGE": "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/1OKRXo9l4D5",
      "PRINT_FRONT": "TYLENOL",
      "PRINT_BACK": "500",
      "DRUG_SHAPE": "장방형",
      "COLOR_CLASS1": "하양",
      "LENG_LONG": "17.6",
      "LENG_SHORT": "7.1",
      "THICK": "5.7",
      "FORM_CODE_NAME": "필름코팅정",
      "CHANGE_DATE": "20260408"
    }]
  }
}
```

**모든 필드 → `ref.pill_visuals` 저장**.

## DB 적재

### Table: `ref.drugs_master`

```sql
CREATE TABLE ref.drugs_master (
    item_seq            VARCHAR(20) PRIMARY KEY,
    item_name           VARCHAR(300) NOT NULL,
    item_name_eng       VARCHAR(300),
    entp_name           VARCHAR(200) NOT NULL,
    entp_no             VARCHAR(20),
    item_permit_date    DATE,
    specialty_type      VARCHAR(20),                       -- '전문의약품' | '일반의약품'
    edi_code            VARCHAR(20),
    atc_code            VARCHAR(10),
    main_ingr_en        VARCHAR(500),                      -- 원본
    main_ingr_norm      VARCHAR(200),                      -- ★ DUR 조인 키 (정규화)
    chart_text          TEXT,
    nb_doc_data         TEXT,                              -- Slice 03 LLM 입력
    ee_doc_data         TEXT,
    ud_doc_data         TEXT,
    source_change_date  DATE,                              -- NULL 허용 (식약처 응답에 없으면)
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drugs_main_ingr_norm ON ref.drugs_master(main_ingr_norm);
CREATE INDEX idx_drugs_atc_code ON ref.drugs_master(atc_code);
CREATE INDEX idx_drugs_edi_code ON ref.drugs_master(edi_code) WHERE edi_code IS NOT NULL;
CREATE INDEX idx_drugs_item_name_trgm ON ref.drugs_master USING gin (item_name gin_trgm_ops);

CREATE TRIGGER trg_drugs_master_updated_at BEFORE UPDATE ON ref.drugs_master
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
```

### Table: `ref.pill_visuals`

```sql
CREATE TABLE ref.pill_visuals (
    item_seq            VARCHAR(20) PRIMARY KEY REFERENCES ref.drugs_master(item_seq) ON DELETE CASCADE,
    image_url           TEXT NOT NULL,
    drug_shape          VARCHAR(50),
    color_primary       VARCHAR(50),
    color_secondary     VARCHAR(50),
    print_front         VARCHAR(100),
    print_back          VARCHAR(100),
    line_front          VARCHAR(50),
    line_back           VARCHAR(50),
    length_long_mm      NUMERIC(5,2),
    length_short_mm     NUMERIC(5,2),
    thickness_mm        NUMERIC(5,2),
    form_name           VARCHAR(50),
    chart_text          TEXT,
    source_change_date  DATE,
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pill_form_name ON ref.pill_visuals(form_name);
CREATE TRIGGER trg_pill_visuals_updated_at BEFORE UPDATE ON ref.pill_visuals
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
```

**캐시 무효화 규약**:
- `source_change_date` 변경 감지: API 응답의 CHANGE_DATE vs DB의 source_change_date
- API 응답에 CHANGE_DATE 없거나 NULL → 30일 이상 경과 시 강제 재호출
- 기본 TTL: 30일

## End-to-End 흐름 (의사코드)

```python
def identify_drug(user_input: str) -> DrugIdentity | DrugCandidates | DrugNotFound:
    # 1) API 1 — 목록
    list_resp = call_mfds_list(item_name=user_input)
    if list_resp.totalCount == 0:
        return DrugNotFound(input=user_input)
    if list_resp.totalCount > 1:
        # 정확 매칭 시도 (대소문자·공백 무시)
        exact = [it for it in list_resp.items if it.ITEM_NAME.strip() == user_input.strip()]
        if len(exact) == 1:
            target = exact[0]
        else:
            return DrugCandidates(items=list_resp.items[:5])
    else:
        target = list_resp.items[0]
    
    item_seq = target.ITEM_SEQ
    
    # 2) drugs_master 캐시 확인
    cached = db.query("SELECT * FROM ref.drugs_master WHERE item_seq = %s", item_seq)
    is_fresh = cached and (
        cached.source_change_date is None and 
        cached.refreshed_at > now() - timedelta(days=30)
    ) or (cached and cached.source_change_date == target.CHANGE_DATE)
    
    if is_fresh:
        return DrugIdentity(from_cache=cached)
    
    # 3) API 2 — 상세
    detail = call_mfds_detail(item_seq=item_seq)
    main_ingr_norm = normalize_drug_name(detail.MAIN_INGR_ENG)
    
    # 4) drugs_master upsert
    db.upsert("ref.drugs_master", item_seq=item_seq, ...)
    
    # 5) API 3 — 낱알식별 (item_name으로 검색이 더 빠름)
    pill_resp = call_mfds_pill_visual(item_name=detail.ITEM_NAME)
    matched = next((p for p in pill_resp.items if p.ITEM_SEQ == item_seq), None)
    if matched:
        db.upsert("ref.pill_visuals", item_seq=item_seq, ...)
    
    return DrugIdentity(from_db=db.fetch_one("ref.drugs_master", item_seq))
```

## API 계약 (이 슬라이스가 노출)

```
GET /v1/drugs/identify?name={user_input}

Response 200:
{
  "item_seq": "202106092",
  "item_name": "타이레놀정500밀리그람(아세트아미노펜)",
  "main_ingr_en": "Acetaminophen",
  "main_ingr_norm": "acetaminophen",
  "atc_code": "N02BE01",
  "edi_code": null,
  "specialty_type": "일반의약품",
  "visual": {
    "image_url": "https://nedrug.mfds.go.kr/.../1OKRXo9l4D5",
    "drug_shape": "장방형",
    "color_primary": "하양",
    "print_front": "TYLENOL",
    "print_back": "500",
    "length": [17.6, 7.1, 5.7],
    "form_name": "필름코팅정"
  }
}

Response 404: { "error": "drug_not_found", "input": "..." }
Response 300 (동명이품): { "candidates": [{...}, ...] }
```

## 수락 기준

- [ ] `ref.drugs_master`, `ref.pill_visuals` 테이블 + 인덱스 + 트리거 생성
- [ ] `GET /v1/drugs/identify?name=타이레놀정500밀리그람(아세트아미노펜)` → `item_seq=202106092` 반환
- [ ] 응답 `visual.print_front == "TYLENOL"`, `visual.print_back == "500"`
- [ ] `ref.drugs_master.main_ingr_norm == "acetaminophen"` (정규화 통과)
- [ ] 두 번째 동일 호출 → 외부 API 호출 0건 (캐시 hit, 로그로 검증)
- [ ] 노바스크정5밀리그람(item_seq=200610660) → `edi_code=073400360`
- [ ] 동명이품 시나리오 (`item_name=노바스크`) → 300 응답 + candidates

## 회귀 자산
- 타이레놀500·노바스크5의 API 응답 fixture를 `tests/fixtures/`에 저장
- 단위 테스트는 fixture, 통합 테스트는 실 API (CI 일 1회)

## 환경변수
- `MFDS_API_KEY`

## 범위 밖
- 동명이품 사용자 선택 UI (프론트엔드)
- DUR 매칭 (Slice 02)
- NB 추출 (Slice 03)
- 부모와 약 연결 (Slice 04)
- 처방전 OCR (별도 PRD)

## Smoke Test
```bash
curl "http://localhost:8000/v1/drugs/identify?name=타이레놀정500밀리그람(아세트아미노펜)" | jq .
psql $DATABASE_URL -c "SELECT item_seq, main_ingr_norm FROM ref.drugs_master;"
```
