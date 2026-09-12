FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :apps:api-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG VERSION
LABEL org.opencontainers.image.version="$VERSION"
LABEL org.opencontainers.image.title="api-service"
LABEL org.opencontainers.image.source="https://github.com/cpolzer/wrkflw"
WORKDIR /app
COPY --from=builder /build/apps/api-service/build/install/api-service .
EXPOSE 8080
CMD ["bin/api-service"]
