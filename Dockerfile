# ---- Build stage: compile the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Download dependencies first so they are cached between builds when only source code changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/*.jar app.jar

# ---- Runtime stage: small JRE image, runs as a non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/data/uploads && chown -R app:app /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar
USER app

# Runtime files that must survive container restarts live in /app/data (mounted as a volume):
# uploaded images and the connections saved from Admin -> Database / Image storage.
ENV UPLOAD_DIR=/app/data/uploads \
    DB_CONFIG_FILE=/app/data/db-connection.json \
    STORAGE_CONFIG_FILE=/app/data/storage-connection.json \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
