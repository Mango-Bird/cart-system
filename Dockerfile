# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
# Integration/context tests need external services; execute them separately.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21.0.8_9-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 cart \
    && useradd --uid 10001 --gid cart --no-create-home cart
WORKDIR /app
COPY --from=build --chown=cart:cart /workspace/target/cart-system-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8081/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
