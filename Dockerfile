# syntax=docker/dockerfile:1
FROM eclipse-temurin:17.0.19_10-jdk AS jdk17

FROM ghcr.io/graalvm/native-image-community:25i2-25.0.4 AS native-builder
ENTRYPOINT []
USER root
COPY --from=jdk17 /opt/java/openjdk /opt/jdk17
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    /opt/jdk17/bin/java -version && \
    ./gradlew :apps:backend:nativeCompile --no-daemon \
      -Dorg.gradle.java.installations.paths=/opt/jdk17

FROM oraclelinux:10-slim
RUN microdnf install -y ca-certificates shadow-utils zlib && \
    microdnf clean all && \
    useradd --system --uid 10001 --no-create-home --shell /sbin/nologin grocery
WORKDIR /app
COPY --from=native-builder --chown=grocery:grocery \
    /workspace/apps/backend/build/native/nativeCompile/grocery-catalog-service \
    /app/grocery-catalog-service
EXPOSE 8080
USER grocery
ENTRYPOINT ["/app/grocery-catalog-service"]
