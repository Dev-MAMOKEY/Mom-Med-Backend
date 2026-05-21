# Slice 00 v2 — 4-schema 인프라 + 공유 유틸

## Why (사용자 가치)
사용자 가치 0. **모든 후속 슬라이스의 토대**. 4-schema 마이그레이션·정규화 유틸·환각 검증기·LLM 클라이언트 래퍼·seed 로더가 이 슬라이스에서 만들어지지 않으면 01~08이 시작 못 함.

## 의존성
없음 (첫 슬라이스).

## v1 → v2 변경
- 4-schema(`ref`/`app`/`derived`/`logs`) 명시 마이그레이션 추가
- 정규화 유틸의 **염 형태 닫힌 목록** (15개) 명시 — 결정성 확보
- `HallucinationVerifier`의 fuzzy 단계 알고리즘 명확화
- LLM 클라이언트 래퍼 (Gemini 2.5 Flash Lite, 변경 가능) 신규
- seed 파일 로더 신규
- 환경변수 정확한 이름 7개 명시 (MFDS/HIRA/KMA/GEMINI/DATABASE/REDIS/APP_ENCRYPTION_KEY)

## v2 → v2.1 변경 (cross-ref 검수 반영)
- 환경변수 섹션 "없음" 모순 해소 → **선언 7개 vs 사용처 매트릭스** 명시

## 범위 (Scope)

### 1. 프로젝트 스캐폴드
- [ ] 백엔드 언어/프레임워크 결정 (추천: Python + FastAPI 또는 TypeScript + NestJS)
- [ ] `.env.example` 완성:
  ```
  MFDS_API_KEY=
  HIRA_API_KEY=
  KMA_API_KEY=
  GEMINI_API_KEY=                       # LLM (Gemini 2.5 Flash Lite, 구현 시 변경 가능)
  DATABASE_URL=postgresql://...
  REDIS_URL=redis://...
  APP_ENCRYPTION_KEY=                    # pgcrypto 컬럼 암호화용 마스터키
  ```
- [ ] Docker Compose: PostgreSQL 14+ + Redis 7+
- [ ] CI 셋업 (GitHub Actions: lint·test·migration dry-run)

### 2. DB 마이그레이션 인프라 (4-schema)

**v001 — extensions**:
```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;          -- 한글 부분검색
CREATE EXTENSION IF NOT EXISTS pgcrypto;         -- UUID + 컬럼 암호화
CREATE EXTENSION IF NOT EXISTS btree_gin;        -- 복합 GIN 인덱스
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

**v002 — schemas**:
```sql
CREATE SCHEMA IF NOT EXISTS ref;       -- Reference Data (외부 마스터)
CREATE SCHEMA IF NOT EXISTS app;       -- Application Data (사용자 생성)
CREATE SCHEMA IF NOT EXISTS derived;   -- Derived Data (가공·캐시)
CREATE SCHEMA IF NOT EXISTS logs;      -- Time-series Logs

-- search_path 기본값 (개발 편의)
ALTER DATABASE mom_med SET search_path TO app, ref, derived, logs, public;
```

**v003 — audit trigger 공통 함수**:
```sql
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 사용 예 (각 테이블에 부착, 슬라이스 01부터):
-- CREATE TRIGGER trg_{table}_updated_at BEFORE UPDATE ON {schema}.{table}
--   FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
```

### 3. 공유 유틸 모듈

**`utils/drug_name_normalizer.py`**:
```python
# 닫힌 염 목록 (결정적 정규화)
SALT_SUFFIXES = [
    "besylate", "maleate", "camsylate", "mesylate", "tosylate",
    "hydrochloride", "hcl", "sulfate", "sulphate", "tartrate",
    "fumarate", "succinate", "acetate", "sodium", "potassium"
]

def normalize_drug_name(name: str) -> str:
    """
    식약처 표기 → DUR 매칭용 base name
    
    Examples (회귀 테스트):
      "Amlodipine Besylate"                  → "amlodipine"
      "amlodipine besylate (as amlodipine)"  → "amlodipine"
      "Acetaminophen"                        → "acetaminophen"
      "Warfarin Sodium"                      → "warfarin"
      "amlodipine camsylate (as amlodipine)" → "amlodipine"
      "암로디핀베실산염"                       → "암로디핀베실산염" (한글은 그대로 — slice 02에서 영문 매칭 후 한글 fallback)
    
    절차:
      1. trim + 소문자화
      2. "(as XXX)" 같은 괄호 안 부가 설명 제거
      3. SALT_SUFFIXES 중 마지막 단어가 매칭되면 제거
      4. 다시 trim
      5. 결과 반환
    """
    # 한글은 그대로 통과 (영문 매칭이 우선, 한글은 fallback)
    if not name.isascii() and not has_latin_char(name):
        return name.strip()
    
    s = name.lower().strip()
    # "(as ...)" 제거
    s = re.sub(r'\s*\([^)]*\)\s*', ' ', s).strip()
    # 마지막 단어가 염이면 제거
    for suffix in SALT_SUFFIXES:
        if s.endswith(f" {suffix}"):
            s = s[:-len(suffix)-1].strip()
            break
    return s
```

**`utils/hallucination_verifier.py`**:
```python
@dataclass
class VerifyFailure:
    quote: str
    file: str
    reason: str  # "not_found" | "found_only_normalized" | "found_only_fuzzy"
    matched_via: str | None  # None / "normalized" / "fuzzy_head" / "fuzzy_tail"

@dataclass
class VerifyResult:
    total: int
    exact: int
    normalized: int  # 공백 정규화 후 일치
    fuzzy: int       # 앞 40자 또는 뒤 40자 substring
    hallucinated: int
    failures: list[VerifyFailure]

def verify_quotes(extraction: dict, source_files: dict[str, str]) -> VerifyResult:
    """
    extraction의 모든 source_citation.quote가 source_files[file]에 
    byte-for-byte 존재하는지 검증.
    
    3단계 매칭 (각 단계는 명확히 정의됨):
      1. exact: source.contains(quote)
      2. normalized: re.sub(r'\\s+', ' ', source).contains(re.sub(r'\\s+', ' ', quote))
      3. fuzzy_head: source.contains(quote[:40])  (quote 길이 ≥ 40일 때만)
         fuzzy_tail: source.contains(quote[-40:]) (quote 길이 ≥ 40일 때만)
      
      3단계 모두 실패 → hallucinated
    
    Returns VerifyResult.
    """
```

**`utils/cache.py`** (Redis 래퍼):
```python
# 키 네임스페이스 예약어 (충돌 방지)
NAMESPACES = {"mfds", "hira", "kma", "llm", "session"}

def cache_get_or_compute(
    key: str,                          # "mfds:drug:202106092"
    compute_fn: Callable[[], Any],
    ttl_seconds: int | None = None,    # None = 영구
    serializer: str = "json",          # "json" 고정 (MVP)
) -> Any:
    """
    Read-through 캐싱.
    Redis 다운 시 → compute_fn으로 fallback, 경고 로그만 남기고 raise 안 함.
    JSON 직렬화. 직렬화 불가능한 타입은 호출자 책임.
    """
```

**`utils/llm_client.py`** (★ v2 신규):
```python
"""
LLM 클라이언트 래퍼 — Gemini 2.5 Flash Lite (구현 단계에서 모델 변경 가능)

대안: Anthropic Claude Sonnet 4, OpenAI GPT-4o.
인터페이스만 안정, 내부 모델 교체는 환경변수로 가능.
"""

GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"

@dataclass
class LLMResponse:
    text: str
    input_tokens: int
    output_tokens: int
    cost_usd: float
    model: str
    prompt_version: str

def call_llm(
    prompt: str,
    *,
    model: str = "gemini-2.5-flash-lite",
    json_mode: bool = True,         # responseMimeType: "application/json"
    max_output_tokens: int = 8192,
    prompt_version: str = "v1",
) -> LLMResponse:
    """
    Gemini 2.5 Flash Lite 호출.
    
    Request body (Google API):
      POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent
      Header: x-goog-api-key: ${GEMINI_API_KEY}
      Body: {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
          "responseMimeType": "application/json" if json_mode else "text/plain",
          "maxOutputTokens": max_output_tokens
        }
      }
    
    Response 파싱:
      response.candidates[0].content.parts[0].text
      response.usageMetadata.promptTokenCount / candidatesTokenCount
    
    메트릭:
      llm_call_tokens{model, kind=input/output}
      llm_call_cost_usd{model}
      llm_call_errors_total{model, error_type}
    
    Retry: 5xx → 1회 재시도 (지수 백오프 1초).
    """
```

**`utils/seed_loader.py`** (★ v2 신규):
```python
def load_seed_file(relative_path: str) -> str:
    """seed/ 하위 텍스트 파일 로드. 환각 검증의 source_files dict 만들 때 사용."""

def load_all_seeds() -> dict[str, str]:
    """seed/ 전체 .md 파일 로드 → {파일경로: 본문} dict 반환."""
```

### 4. 로깅·관측성
- 구조화 로깅 (JSON)
- 메트릭 (Prometheus 형식):
  - `external_api_call_seconds{api, status}`
  - `external_api_errors_total{api, error_type}`
  - `llm_call_tokens{model, kind}`
  - `llm_call_cost_usd{model}`
  - `db_query_seconds{schema, operation}`

## 수락 기준

- [ ] `docker-compose up` → PostgreSQL 14+ + Redis 7+ 정상 기동
- [ ] `alembic upgrade head` → 4 schema(ref/app/derived/logs) + 3 extension(pg_trgm/pgcrypto/btree_gin) + audit 트리거 함수 생성 확인
- [ ] `normalize_drug_name` 단위 테스트 (위 6개 예시 모두 통과)
- [ ] `verify_quotes` 단위 테스트:
  - 원문 일치 → exact
  - 공백 변경 → normalized
  - 변조된 quote → hallucinated
- [ ] `call_llm("ping")` → 정상 응답 (Gemini API 키 환경변수 설정 시)
- [ ] `load_all_seeds()` → seed/ 의 6개 md 파일 모두 로드
- [ ] `.env.example` 7개 변수 모두 명시
- [ ] CI lint·test 그린

## 환경변수

이 슬라이스에선 **7개 환경변수를 선언만** 합니다 (`.env.example` 작성).
실제 사용은 후속 슬라이스에서:

| 변수 | 선언 (이 슬라이스) | 실제 사용처 |
|---|---|---|
| `MFDS_API_KEY` | ✅ | 01 (약 마스터·낱알식별) |
| `HIRA_API_KEY` | ✅ | 02 (DUR), 05 (age/preg 룰셋 ETL), 08 (병원·약국) |
| `KMA_API_KEY` | ✅ | 07 (기상청 ETL) |
| `GEMINI_API_KEY` | ✅ | 03 (NB 추출 LLM) — 이 슬라이스에선 `call_llm("ping")` 통합 테스트 권장 |
| `DATABASE_URL` | ✅ | 전체 슬라이스 |
| `REDIS_URL` | ✅ | 04 (rate limit), 07 (ETL 잡 락·부모 푸시 락) |
| `APP_ENCRYPTION_KEY` | ✅ | 04 (device_tokens `pgp_sym_encrypt`), 07 (`pgp_sym_decrypt`) |

★ v2.1: "없음" 표기 → "선언 7개, 사용 0건"으로 명확화 (cross-ref 검수 반영)

## 범위 밖
- 비즈니스 로직 (슬라이스 01~08)
- 실제 외부 API 호출 (슬라이스 01부터)
- 프론트엔드
- LLM 비용·rate limit 모니터링 (MVP 후 처리)

## Smoke Test
```bash
docker-compose up -d
alembic upgrade head
psql $DATABASE_URL -c "\dn" | grep -E '^ (ref|app|derived|logs)$'
pytest tests/utils/
```

## 정의 (Done)
- [ ] PR 머지
- [ ] 모든 단위 테스트 그린
- [ ] 다른 개발자가 README만 보고 `docker-compose up` → 환경 기동 가능
- [ ] `alembic upgrade head`로 4-schema 인프라 완전 재현 가능
