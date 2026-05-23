# Flyway Smoke Test

이 문서는 로컬 PostgreSQL 컨테이너에서 Flyway 마이그레이션이 실제로 적용되는지 확인하는 절차입니다.
실제 비밀번호와 API 키는 `.env`에만 두고, 이 문서와 Git에는 남기지 않습니다.

## 실행

PostgreSQL 컨테이너를 먼저 실행합니다.

```powershell
docker compose up -d
docker ps
```

Docker Compose PostgreSQL을 사용할 때 `.env`에는 컨테이너 생성값과 Spring Boot 접속 URL을 같은 DB로 맞춥니다.

```env
POSTGRES_DB=mom_med
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-local-password
POSTGRES_PORT=15432
DATABASE_URL=jdbc:postgresql://127.0.0.1:15432/mom_med
REDIS_URL=redis://localhost:6379
```

로컬 `5432` 포트가 이미 사용 중이면 `POSTGRES_PORT`와 `DATABASE_URL`의 포트를 함께 바꿉니다.

```env
POSTGRES_PORT=15432
POSTGRES_DB=mom_med
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-local-password
DATABASE_URL=jdbc:postgresql://127.0.0.1:15432/mom_med
```

Spring Boot를 migration smoke 용도로 한 번만 기동합니다.
Redis는 이 smoke 범위가 아니므로 Redisson 자동 설정만 제외합니다.

```powershell
java -jar build\libs\backend-0.0.1-SNAPSHOT.jar `
  --spring.main.web-application-type=none `
  --spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4
```

## DB 확인

컨테이너 내부 psql로 접속합니다.

```powershell
docker exec -it postgres psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}
```

Flyway 적용 이력과 생성된 schema/table을 확인합니다.

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

## 유지보수 주의

Flyway 마이그레이션 파일은 checksum으로 관리됩니다.
한 번 DB에 적용한 `V001`, `V002` 같은 파일은 원칙적으로 수정하지 말고 새 버전 파일을 추가해야 합니다.
이미 적용된 파일을 수정하면 기존 로컬 DB에서는 checksum mismatch가 발생할 수 있으므로,
필요할 때만 DB volume을 재생성하거나 Flyway repair 절차를 검토하세요.
