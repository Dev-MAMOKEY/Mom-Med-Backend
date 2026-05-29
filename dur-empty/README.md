# dur-empty (placeholder)

이 디렉토리는 docker-compose의 `${DUR_DATA_DIR:-./dur-empty}` 기본값 fallback용 빈 폴더입니다.
**삭제하지 마세요** — 없으면 `docker compose up`이 read-only 마운트에 실패합니다.

## DUR CSV ETL 켜는 법

실제 DUR CSV 5개가 있는 호스트 폴더를 `.env`에 지정하세요:

```env
DUR_DATA_DIR=C:/path/to/dur-csv
APP_ETL_DUR_ENABLED=true
```

이 폴더 안에는 다음 5개 파일이 있어야 합니다 (application-docker.yml의 기본 경로):
- `combo.csv` (병용금기)
- `elderly.csv` (노인주의)
- `elderly_nsaid.csv` (노인주의 NSAID)
- `age.csv` (연령금기)
- `pregnancy.csv` (임부금기)

또는 환경변수로 개별 파일 경로 override:
```env
APP_ETL_DUR_COMBO_PATH=/data/dur/의약품안전사용서비스(DUR)_병용금기 품목리스트 2025.6.csv
...
```
