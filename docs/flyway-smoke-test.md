# Flyway Smoke Test

이 문서는 로컬 PostgreSQL 컨테이너에서 Flyway 마이그레이션이 실제로 적용되는지 확인하는 절차입니다.
실제 비밀번호와 API 키는 `.env`에만 두고, 이 문서나 Git에는 남기지 않습니다.

## 실행

```powershell
docker compose up -d
docker ps
```

로컬 `5432` 포트가 이미 다른 PostgreSQL에서 사용 중이면 `.env`에 아래처럼 호스트 포트를 바꿉니다.

```env
POSTGRES_PORT=15432
DATABASE_URL=jdbc:postgresql://127.0.0.1:15432/mom_med
```

Spring Boot를 migration smoke 용도로 한 번만 기동합니다. Redis는 이 smoke 범위가 아니므로 Redisson 자동 설정만 제외합니다.

```powershell
java -jar build\libs\backend-0.0.1-SNAPSHOT.jar `
  --spring.main.web-application-type=none `
  --spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4
```

## DB 확인

```powershell
docker exec -it postgres psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}
```

```sql
SELECT version, description, success
FROM app.flyway_schema_history
ORDER BY installed_rank;

SELECT schemaname, tablename
FROM pg_tables
WHERE schemaname IN ('app', 'ref', 'derived', 'logs')
ORDER BY schemaname, tablename;

SELECT schema_name
FROM information_schema.schemata
WHERE schema_name IN ('ref', 'app', 'derived', 'logs')
ORDER BY schema_name;
```

## 기대 결과

- `app.flyway_schema_history`가 생성됩니다.
- `V001`부터 `V004`까지 `success = true`입니다.
- `ref`, `app`, `derived`, `logs` schema가 생성됩니다.
- `app.users` smoke table이 생성됩니다.
