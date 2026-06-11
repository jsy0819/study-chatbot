# syntax=docker/dockerfile:1

# ── Build stage ───────────────────────────────────────────────
# 호스트의 build/ 산출물에 의존하지 않고, Gradle Wrapper로 컨테이너 안에서
# 직접 실행형 jar(bootJar)를 생성한다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 빌드 스크립트와 래퍼를 먼저 복사해 의존성 다운로드 레이어를 캐싱한다.
# (소스만 바뀌면 이 레이어를 재사용 → 재빌드가 빨라진다)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 실행형 jar 생성. (-plain.jar는 만들지 않는 bootJar만 실행)
COPY src src
RUN ./gradlew bootJar --no-daemon

# ── Runtime stage ─────────────────────────────────────────────
# 경량 JRE 이미지에 빌드된 jar만 복사해 최종 이미지를 가볍게 유지한다.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 빌드 스테이지에서 생성된 실행형 jar 복사 (-plain.jar 제외, 패턴이 하나만 매칭)
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# 문서용 노출 포트. 실제 바인딩 포트는 application.properties의
# server.port=${PORT:8080}가 결정한다. Railway는 PORT 환경변수를 주입한다.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
