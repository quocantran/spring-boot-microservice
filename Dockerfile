# Multi-stage Dockerfile for all Spring Boot microservices
# Usage: docker build --build-arg SERVICE=auth-service -t auth-service .

# ── Stage 1: Build ──────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS build
ARG SERVICE
WORKDIR /app

# Copy all POM files first for dependency caching
COPY pom.xml .
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY auth-service/pom.xml auth-service/
COPY booking-service/pom.xml booking-service/
COPY seat-service/pom.xml seat-service/
COPY movie-service/pom.xml movie-service/
COPY payment-service/pom.xml payment-service/
COPY ai-recommender-service/pom.xml ai-recommender-service/

# Download dependencies (layer cached unless POMs change)
RUN mvn dependency:go-offline -pl ${SERVICE} -am -B 2>/dev/null || true

# Copy source code (common is always needed)
COPY common/src common/src
COPY ${SERVICE}/src ${SERVICE}/src

# Build the service
RUN mvn clean package -pl ${SERVICE} -am -DskipTests -B -q \
    && mv ${SERVICE}/target/${SERVICE}-*.jar /app/app.jar

# ── Stage 2: Runtime ────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
