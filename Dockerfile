# =============================================================================
# 엄마약 백엔드 Docker 이미지 — Multi-stage build
# Stage 1 (builder): JDK 21 + gradle로 jar 빌드
# Stage 2 (runtime): JRE 21 alpine — 가벼운 실행 환경
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — Gradle 빌드
# eclipse-temurin: Adoptium의 공식 OpenJDK 배포판 (Spring Boot 권장)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# gradle wrapper와 빌드 스크립트를 먼저 복사 — 의존성 캐시 레이어 활용
# (소스만 바뀌었을 때 의존성 다운로드 스킵)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 의존성 다운로드 (소스가 없으니 실제 빌드는 실패하지만 캐시는 채워짐)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 실제 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# -----------------------------------------------------------------------------
# Stage 2 — Runtime
# JRE 21 — 빌드 도구·캐시 없이 실행만. 이미지 크기 최소화.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# 비루트 사용자로 실행 — 보안 권장
RUN groupadd --system spring && useradd --system --gid spring spring

# 빌더 stage에서 생성된 jar만 복사 (와일드카드: 버전 변경에 안전)
COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN chown spring:spring app.jar

USER spring

# dev001의 WSL/IPv4 회피 — JVM이 IPv4를 우선 사용하도록.
# 컨테이너 네트워크에서도 IPv6 듀얼스택 환경 일관성 확보.
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"

EXPOSE 8080

# Spring Boot 기본 헬스 엔드포인트로 컨테이너 헬스체크
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/v3/api-docs || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
