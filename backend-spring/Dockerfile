# syntax=docker/dockerfile:1.7

# ---- Build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies before copying sources for faster incremental builds.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package \
 && cp target/*.jar /build/app.jar

# ---- Runtime stage --------------------------------------------------------
# Using -jammy (Ubuntu) instead of -alpine because eclipse-temurin alpine
# variants lack linux/arm64 manifests as of late 2024 (Apple Silicon hosts
# would fail with "no match for platform in manifest").
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system spring \
 && useradd --system --gid spring --shell /usr/sbin/nologin spring \
 && apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=spring:spring /build/app.jar /app/app.jar

USER spring
EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
