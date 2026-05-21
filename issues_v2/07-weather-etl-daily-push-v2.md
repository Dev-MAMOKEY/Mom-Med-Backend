# Slice 07 v2 — 기상청 ETL + 자체 판정 + 자녀 푸시

## Why (사용자 가치)
**기능 5의 결정적 슬라이스**. 매일 새벽 ETL이 부모님 동네 기상예보를 가져와 폭염·한파 단계를 자체 판정. 그 단계 × 부모님 기저질환으로 룰셋 메시지를 자녀에게 푸시.

## 의존성 (★ v1→v2 추가)
- Slice 00 v2: 4-schema, `cache_get_or_compute`
- Slice 04 v2: `app.patient_profiles` (nx, ny), `app.device_tokens`
- **Slice 05 v2**: `app.patient_conditions` (룩업 시 disease_codes) ★ v1엔 누락
- Slice 06 v2: `ref.weather_rules`, `lookup_rules()`, `WeatherAdvisory` (rule_id 포함)

## v1 → v2 변경
- DDL에 schema prefix (`logs.*` 파티션)
- 의존성 05 명시 추가 (`app.patient_conditions` JOIN)
- `app.device_tokens` JOIN으로 푸시 (slice 04 신규)
- 우선순위 정책 결정 명시 (1일 1푸시 + 강도 위험 우선)
- TMX 추출 대상 일자 명확화 (오늘만, 내일 TMN은 한파용)
- 알람 피로 정책 충돌 해결

## v2 → v2.1 변경 (cross-ref 검수 반영)
- `delivery_status` enum에 **`skipped_review`** 추가 (fatigue와 분리 — 운영 메트릭)
- **Redis 락 2단** 추가: 잡 레벨(중복 실행 방지) + 부모 레벨(동일-일 1푸시 DB 제약 대체)

## 외부 API 호출 명세

### API 1 — 기상청 단기예보 조회서비스
- **endpoint**: `https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst`
- **method**: GET · JSON
- **일일 한도**: 10,000건/오퍼레이션

| 파라미터 | 필수 | 값 출처 | 예시 |
|---|---|---|---|
| `serviceKey` | ✅ | env.KMA_API_KEY | f204... |
| `pageNo` | ✅ | 1 | 1 |
| `numOfRows` | ✅ | 900 | 900 |
| `dataType` | ✅ | JSON | JSON |
| `base_date` | ✅ | 오늘 KST YYYYMMDD | 20260520 |
| `base_time` | ✅ | 0500 | 0500 |
| `nx` | ✅ | `app.patient_profiles.nx` | 89 |
| `ny` | ✅ | `app.patient_profiles.ny` | 90 |

```bash
curl -s "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=${KMA_API_KEY}&pageNo=1&numOfRows=900&dataType=JSON&base_date=20260520&base_time=0500&nx=89&ny=90"
```

**응답 핵심 카테고리**:

| category | 의미 | 사용 |
|---|---|---|
| **TMX** | 일 최고기온 (℃) | ★ 폭염 판정 (오늘) |
| **TMN** | 일 최저기온 (℃) | ★ 한파 판정 (내일 아침) |
| TMP | 3시간 기온 | 참고 |

```json
{
  "response": {
    "body": {
      "items": {
        "item": [
          { "category": "TMX", "fcstDate": "20260520", "fcstTime": "1500", "fcstValue": "18.0", "nx": 89, "ny": 90 },
          { "category": "TMN", "fcstDate": "20260521", "fcstTime": "0600", "fcstValue": "16.0" },
          ...
        ]
      }
    }
  }
}
```

## DB 적재

### Table: `logs.weather_observations_daily` (월별 파티션)

```sql
CREATE TABLE logs.weather_observations_daily (
    id                  BIGSERIAL,
    observed_date       DATE NOT NULL,
    nx                  SMALLINT NOT NULL,
    ny                  SMALLINT NOT NULL,
    tmx                 NUMERIC(4,1),
    tmn                 NUMERIC(4,1),                      -- 내일 아침 (한파 판정용)
    derived_alerts      JSONB NOT NULL DEFAULT '[]'::jsonb,
    base_date           VARCHAR(8) NOT NULL,
    base_time           VARCHAR(4) NOT NULL,
    raw_response        JSONB,
    fetched_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (observed_date, id),
    UNIQUE (observed_date, nx, ny)
) PARTITION BY RANGE (observed_date);

CREATE TABLE logs.weather_observations_daily_2026_05
    PARTITION OF logs.weather_observations_daily
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE INDEX idx_weather_obs_grid ON logs.weather_observations_daily(nx, ny, observed_date);
```

### Table: `logs.advisory_push_log` (월별 파티션)

```sql
CREATE TABLE logs.advisory_push_log (
    id                  BIGSERIAL,
    parent_id           UUID NOT NULL,
    rule_id             BIGINT NOT NULL REFERENCES ref.weather_rules(id),
    weather_obs_id      BIGINT,
    push_payload        JSONB NOT NULL,
    delivery_status     VARCHAR(20) NOT NULL CHECK (delivery_status IN ('sent','failed','simulated','skipped_fatigue','skipped_review')),
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sent_at, id)
) PARTITION BY RANGE (sent_at);

CREATE TABLE logs.advisory_push_log_2026_05
    PARTITION OF logs.advisory_push_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE INDEX idx_push_log_parent_date ON logs.advisory_push_log(parent_id, sent_at DESC);

-- 같은 (parent, rule, 날짜) 1회 제한 — 파티션이라 partial unique 어려움 → 앱 레이어로 확인
```

## ETL 잡 흐름 (의사코드)

```python
# 스케줄: 매일 05:30 KST
def daily_weather_advisory_job():
    today = (datetime.now(KST)).date()
    base_date = today.strftime("%Y%m%d")
    tomorrow = today + timedelta(days=1)
    
    # ★ v2.1: 잡 레벨 Redis 락 — 동일 일자 중복 실행 방지 (재시도·수동 트리거 안전)
    # DB-level partial UNIQUE는 파티션 테이블 제약으로 어려움 → Redis nx로 보완
    job_lock_key = f"weather_etl:job:{today.isoformat()}"
    if not redis.set(job_lock_key, "1", nx=True, ex=1800):  # 30분 TTL = 잡 timeout과 일치
        log.warn("daily_weather_advisory_job: 이미 다른 워커가 실행 중", date=today)
        return {"status": "skipped_duplicate_run"}
    
    # 1) 활성 부모 (격자 좌표 있음 + 데이터 공유 동의)
    parents = db.execute("""
        SELECT parent_id, display_name, nx, ny, address_sido, address_sigungu
        FROM app.patient_profiles
        WHERE consent_data_share = TRUE
          AND nx IS NOT NULL AND ny IS NOT NULL
    """).all()
    
    # 2) unique 격자만 추출 (같은 좌표 부모 여럿 → 1회 호출)
    unique_grids = set((p.nx, p.ny) for p in parents)
    
    # 3) 격자별 기상청 API 호출
    obs_by_grid = {}
    for nx, ny in unique_grids:
        cached = db.fetch_one("logs.weather_observations_daily",
                              observed_date=today, nx=nx, ny=ny)
        if cached:
            obs_by_grid[(nx, ny)] = cached
            continue
        
        response = call_kma_api(base_date=base_date, base_time="0500", nx=nx, ny=ny)
        if response.resultCode != "00":
            log.error("KMA API 실패", nx=nx, ny=ny, msg=response.resultMsg)
            continue
        
        items = response.body.items.item
        # ★ v2 명확화: TMX는 오늘 / TMN은 내일 아침 (한파 판정 정확도)
        tmx = next((float(i.fcstValue) for i in items 
                    if i.category=="TMX" and i.fcstDate == base_date), None)
        tmn = next((float(i.fcstValue) for i in items 
                    if i.category=="TMN" and i.fcstDate == tomorrow.strftime("%Y%m%d")), None)
        
        # 4) 자체 임계값 판정
        alerts = []
        if tmx is not None:
            if tmx >= 35: alerts.append("폭염경보")
            elif tmx >= 33: alerts.append("폭염주의보")
        if tmn is not None:
            if tmn <= -15: alerts.append("한파경보")
            elif tmn <= -12: alerts.append("한파주의보")
        
        obs = db.insert("logs.weather_observations_daily",
            observed_date=today, nx=nx, ny=ny,
            tmx=tmx, tmn=tmn, derived_alerts=alerts,
            base_date=base_date, base_time="0500", raw_response=response.dict()
        )
        obs_by_grid[(nx, ny)] = obs
    
    # 5) 부모별 룰 룩업 + 우선순위 + 알람 피로 방지 + 푸시
    for parent in parents:
        # ★ v2.1: 부모 레벨 Redis 락 — 동일 (parent, day) 1푸시 보장 (DB UNIQUE 대체)
        parent_lock_key = f"weather_etl:parent:{parent.parent_id}:{today.isoformat()}"
        if not redis.set(parent_lock_key, "1", nx=True, ex=300):  # 5분 TTL
            log.debug("이미 처리됨 또는 처리 중", parent_id=parent.parent_id)
            continue
        
        obs = obs_by_grid.get((parent.nx, parent.ny))
        if not obs or not obs.derived_alerts:
            continue
        
        # 부모 기저질환 (slice 05의 JOIN)
        disease_codes = db.execute("""
            SELECT disease_code FROM app.patient_conditions
            WHERE parent_id = :pid AND deleted_at IS NULL
        """, {"pid": parent.parent_id}).scalars().all()
        
        advisories = lookup_rules(disease_codes=disease_codes, 
                                  weather_alerts=obs.derived_alerts)
        
        # ★ v2 우선순위 정책 (충돌 해결):
        # - 부모 1명당 1일 최대 1푸시 (알람 피로 강력 방지)
        # - severity: 위험 > 경고 > 주의 > 관심 순으로 최강 1개 선택
        if not advisories:
            continue
        severity_order = {"위험": 4, "경고": 3, "주의": 2, "관심": 1}
        top = max(advisories, key=lambda a: severity_order.get(a.severity, 0))
        
        # 알람 피로: 같은 (parent, rule, day) 이미 보냈으면 skip
        already_sent = db.execute("""
            SELECT 1 FROM logs.advisory_push_log
            WHERE parent_id = :pid AND rule_id = :rid 
              AND DATE(sent_at) = :today
        """, {"pid": parent.parent_id, "rid": top.rule_id, "today": today}).first()
        if already_sent:
            continue
        
        # ★ v2: requires_review 룰은 푸시 안 함 (사용자 신뢰 보호)
        # 정책 결정: skip + log (별도 enum 값으로 분리 — 운영 메트릭에서 fatigue와 review를 구분)
        if top.requires_review:
            db.insert("logs.advisory_push_log", parent_id=parent.parent_id,
                rule_id=top.rule_id, weather_obs_id=obs.id,
                push_payload={"skipped_reason": "rule_under_review"},
                delivery_status="skipped_review")  # ★ v2.1: skipped_fatigue와 분리
            continue
        
        # 호칭 치환
        message = top.message.replace("어머님", parent.display_name)
        
        # 디바이스 토큰 (slice 04의 device_tokens JOIN, 암호화 복호화 필요)
        tokens = db.execute("""
            SELECT pgp_sym_decrypt(token_encrypted, :key) AS token, platform
            FROM app.device_tokens
            WHERE parent_id = :pid AND revoked_at IS NULL
        """, {"pid": parent.parent_id, "key": APP_ENCRYPTION_KEY}).all()
        
        push_payload = {
            "title": top.title,
            "body": message,
            "data": {
                "parent_id": str(parent.parent_id),
                "rule_id": top.rule_id,
                "weather_alert": top.weather_alert,
                "source_citations": top.source_citations,
            }
        }
        
        for tok in tokens:
            result = push_service.send(tok.token, push_payload, platform=tok.platform)
            db.insert("logs.advisory_push_log",
                parent_id=parent.parent_id, rule_id=top.rule_id,
                weather_obs_id=obs.id, push_payload=push_payload,
                delivery_status="sent" if result.ok else "failed")
```

## 우선순위 정책 (★ v2 결정)

| 정책 | 결정 |
|---|---|
| 부모 1명당 일일 푸시 수 | **1개 (최대)** |
| 매칭 룰 다수일 때 | **severity 위험 > 경고 > 주의 순 최강 1개** |
| 같은 (부모, 룰, 날짜) 중복 | **skip** (`logs.advisory_push_log` 조회) |
| `requires_review` 룰 | **푸시 안 함** (검수 후 활성화) |
| 약물 안전 푸시와 동시 매칭 | 약물 안전 우선, 날씨는 30분 뒤 (slice 04와 정책 정렬) |

## API 계약

```
POST /v1/admin/weather/etl-run
  Request: { "base_date": "20260520" (선택, 미지정 시 오늘) }
  Response 200:
  {
    "started_at": "...",
    "grids_fetched": 5,
    "advisories_sent": 12,
    "fatigue_skipped": 3,
    "review_skipped": 5,
    "status": "completed"        // 또는 "skipped_duplicate_run" (Redis 락 충돌 시)
  }

GET /v1/parents/{parent_id}/weather-advisory?date=YYYY-MM-DD
  Response 200:
  {
    "parent_id": "...",
    "date": "...",
    "grid": { "nx": 89, "ny": 90 },
    "observed": { "tmx": 36.5, "tmn": null },
    "active_alerts": ["폭염경보"],
    "advisories": [
      {
        "rule_id": 5,
        "disease_code": "I10",
        "severity": "위험",
        "title": "...",
        "message": "...",
        "drugs": [...],
        "source": [...],
        "already_pushed": true,
        "pushed_at": "..."
      }
    ]
  }

GET /v1/admin/weather/recent-pushes?days=7
  Response: 최근 푸시 로그 (운영 모니터링)
```

## 스케줄러 설정

```yaml
# Kubernetes CronJob 또는 systemd timer
schedule: "30 5 * * *"   # 매일 05:30 KST
job: daily_weather_advisory_job
timeout: 30분
retry: 3회 (실패 시)
alert_on_failure: 운영팀
```

## 수락 기준

- [ ] `logs.weather_observations_daily`·`logs.advisory_push_log` 파티션 테이블 생성 + 첫 파티션
- [ ] cron 트리거로 ETL 매일 자동 실행
- [ ] 시뮬: TMX=36 mock → derived_alerts=["폭염경보"] + 부모 I10에 푸시 1건
- [ ] **알람 피로**: 같은 부모·룰·날짜 두 번째 호출 → push_log에 `skipped_fatigue` 카운트만 추가, 푸시 안 보냄
- [ ] **우선순위**: 부모에게 I10(위험) + E11(주의) 매칭 → I10만 1푸시 (위험 우선)
- [ ] **requires_review 정책**: 미검수 룰은 푸시 안 함 + log에 **`skipped_review`** 기록 (v2.1: fatigue와 분리)
- [ ] **Redis 락 중복방지** (v2.1): 같은 일자 ETL을 2번 트리거 → 두 번째는 `skipped_duplicate_run` 반환, 부모 푸시 발생 0건
- [ ] 평상 날씨 → 푸시 0
- [ ] device_tokens 복호화 정상 동작
- [ ] 부모가 conditions 없으면 빈 결과 + 푸시 안 함
- [ ] 단일 격자 ETL < 1초, 전체 (≤30 격자) < 30초

## 회귀 자산
- (별도 없음 — 실측 응답을 fixture로 생성 권장)
- Slice 06 룰셋 + 04 patient_profiles fixture

## 환경변수
- `KMA_API_KEY` (기상청)
- `APP_ENCRYPTION_KEY` (device_tokens 복호화)
- `REDIS_URL` (★ v2.1: ETL 잡 락 + 부모별 1푸시 락)
- 푸시 서비스 키 (FCM 등 — 별도 PRD)

## 범위 밖
- **알람 푸시 인프라 자체** (FCM/APNs 셋업) — 별도 PRD. 이 슬라이스는 `push_service.send()` 인터페이스만 호출
- **자녀 앱 푸시 UI**
- **Phase 2 기상** (미세먼지·황사·건조·일교차)
- **부모 위치 자동 추정** (현재는 등록 주소)
- **여행 중인 부모** (다른 도시)

## Smoke Test
```bash
# 수동 ETL
curl -X POST "http://localhost:8000/v1/admin/weather/etl-run" | jq .

# 부모 advisory 조회
curl "http://localhost:8000/v1/parents/${PARENT_ID}/weather-advisory" | jq .

# 푸시 로그
curl "http://localhost:8000/v1/admin/weather/recent-pushes?days=1" | jq .
```
