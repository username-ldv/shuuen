FROM golang:1.26.6-alpine3.24 AS build

WORKDIR /src
ENV CGO_ENABLED=0 GOOS=linux

COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download

COPY . .
# The cache mounts keep Go's module and build caches between builds, so a
# rebuild (including one from `docker compose up --watch`) only compiles what
# changed. The seed command ships too: `docker compose exec api ./seed-c-tonic`.
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go build -trimpath -ldflags="-s -w" -o /out/shuuen-api ./cmd/api \
    && go build -trimpath -ldflags="-s -w" -o /out/seed-c-tonic ./cmd/seed-c-tonic

FROM alpine:3.24.1

WORKDIR /app
RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S shuuen \
    && adduser -S -G shuuen shuuen \
    && mkdir -p /app/data \
    && chown -R shuuen:shuuen /app

COPY --from=build --chown=shuuen:shuuen /out/ /app/

USER shuuen

EXPOSE 9999
# The start period has to cover the startup catalog scan, which grows with the
# library. Until it passes, start-interval probes every second, so a fast start
# is still reported immediately.
HEALTHCHECK --interval=30s --timeout=3s --start-period=120s --start-interval=1s --retries=3 \
    CMD wget -qO- http://127.0.0.1:9999/healthz >/dev/null || exit 1
CMD ["/app/shuuen-api"]
