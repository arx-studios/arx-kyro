# syntax=docker/dockerfile:1
# KYRO API image. Build context: repository root.
#   docker build -f infrastructure/docker/api.Dockerfile -t kyro-api .

FROM eclipse-temurin:25-jdk AS build
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
WORKDIR /workspace

# Gradle wrapper and build definition first (better layer caching).
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY apps/api/build.gradle.kts ./apps/api/build.gradle.kts
# OpenAPI contract is the input of the Java code generator.
COPY packages/api-spec/openapi.yaml ./packages/api-spec/openapi.yaml
RUN chmod +x gradlew && ./gradlew --no-daemon --version

COPY apps/api/src ./apps/api/src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :apps:api:bootJar -x test \
    && mkdir -p /out \
    && cp "$(find apps/api/build/libs -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /out/app.jar

FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system --gid 10001 kyro \
    && useradd --system --uid 10001 --gid kyro --no-create-home --shell /usr/sbin/nologin kyro
WORKDIR /app
COPY --from=build --chown=kyro:kyro /out/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
