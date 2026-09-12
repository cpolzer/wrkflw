FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :apps:worker-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG VERSION
LABEL org.opencontainers.image.version="$VERSION"
LABEL org.opencontainers.image.title="worker-service"
LABEL org.opencontainers.image.source="https://github.com/cpolzer/wrkflw"
WORKDIR /app
COPY --from=builder /build/apps/worker-service/build/install/worker-service .
CMD ["bin/worker-service"]
