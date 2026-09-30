# syntax=docker/dockerfile:1
#
# CineScout as a single container: the backend serving the web app itself, for a platform that runs one
# service with no web server in front (Render's free tier, say). Build from the repository root:
#   docker build -f deploy/render/Dockerfile .
# The usual deployment (docker-compose.yml) keeps the two apart, with nginx in front; this one does not need it.

# --- the web app ---------------------------------------------------------------------------------
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY frontend/ ./
# Map tiles: the public OpenStreetMap ones unless a provider is named (see the README).
ARG VITE_MAP_TILE_URL=
ARG VITE_MAP_TILE_ATTRIBUTION=
RUN npm run build

# --- the backend, with the web app inside ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B clean package -DskipTests && cp target/cinescout-backend-*.jar /app.jar

# --- run: a JRE, as an unprivileged user ---------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system cinescout && useradd --system --gid cinescout --no-create-home cinescout
COPY --from=build /app.jar /opt/cinescout/app.jar
USER cinescout
# The platform names the port (PORT); 8081 when it does not.
EXPOSE 8081
# Sized for a small instance (512 MB). The heap gets 40% of the container; the rest is capped too: class
# metadata, compiled code (the quick C1 compiler only, which also starts faster), direct buffers and Netty's
# buffer pools, so the JVM stays well inside the limit instead of being killed at it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -Xss512k -Dio.netty.allocator.numDirectArenas=1 -Dio.netty.allocator.numHeapArenas=1 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/opt/cinescout/app.jar"]
