# ECZAM backend — multi-stage build.
# Stage 1 builds the fat jar with Maven; stage 2 runs it on a slim JRE.

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first: copy the POM, resolve, then copy sources.
COPY pom.xml ./
RUN mvn -q -B -o dependency:go-offline 2>/dev/null || mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests clean package

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# curl is used by the container healthcheck; non-root user for the running app.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 eczam
COPY --from=build /app/target/*.jar /app/app.jar
USER eczam
EXPOSE 8080
# Spring Boot Actuator health (under the /api/v1 context path).
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=10 \
    CMD curl -fsS http://localhost:8080/api/v1/actuator/health || exit 1
# Honors all env vars defined in application.yml (DATABASE_URL, JWT_SECRET, …).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
