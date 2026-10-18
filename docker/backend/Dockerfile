# Mobile Test Playground backend (Kotlin/Ktor). Multi-stage: build the fat JAR with the Gradle
# wrapper, then run it on a JRE. Build context: the backend/ directory.

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /src
# Wrapper and build scripts first, so dependency resolution is cached across source changes.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY config/ config/
RUN ./gradlew --no-daemon --version > /dev/null
COPY src/ src/
RUN ./gradlew --no-daemon buildFatJar -x test

FROM eclipse-temurin:17-jre-jammy
# curl for the container health check.
RUN apt-get update && apt-get install --no-install-recommends -y curl && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /bin/bash app
USER app
WORKDIR /app
COPY --from=build /src/build/libs/backend.jar /app/backend.jar
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=12 \
    CMD curl -fsS http://localhost:8080/ready || exit 1
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
