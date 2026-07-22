# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

COPY config config
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine
RUN apk upgrade --no-cache \
    && addgroup -S registry \
    && adduser -S -G registry registry \
    && mkdir -p /var/cache/registry-seed \
    && chown registry:registry /var/cache/registry-seed
WORKDIR /app
COPY --from=build --chown=registry:registry /workspace/build/libs/private-registry-api-*.jar app.jar
USER registry:registry
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD wget --quiet --spider http://localhost:8080/health/ready || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
