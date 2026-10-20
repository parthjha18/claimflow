# ---- build stage: full JDK 21 + Maven; dependencies cached in their own layer ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

# ---- runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 1001 claimflow \
    && mkdir -p /data/uploads && chown -R claimflow /data
USER claimflow
WORKDIR /app
COPY --from=build /build/target/claimflow.jar app.jar
ENV STORAGE_ROOT=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
