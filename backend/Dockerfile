# syntax=docker/dockerfile:1

# --- build: the Spring Boot jar -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first, so a code change does not download them again.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B clean package -DskipTests \
    && cp target/cinescout-backend-*.jar /app.jar

# --- run: a JRE, as an unprivileged user ----------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system cinescout && useradd --system --gid cinescout --no-create-home cinescout
COPY --from=build /app.jar /opt/cinescout/app.jar
USER cinescout
EXPOSE 8081
# Sized from the container's memory limit rather than the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/opt/cinescout/app.jar"]
