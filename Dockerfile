# --------------------------------------------------------
# Builder Stage (빌드 단계)
# --------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Gradle 캐싱을 위해 설정 파일만 먼저 복사 (빌드 속도 최적화)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 실행 권한 부여
RUN chmod +x ./gradlew

# 의존성 설치
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사 및 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon

# --------------------------------------------------------
# Runner Stage (실행 단계)
# --------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Builder 단계에서 생성된 JAR 파일만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너 실행 시 사용할 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]