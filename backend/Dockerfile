FROM golang:1.26.5-alpine3.24 AS build

WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/shuuen-api ./cmd/api

FROM alpine:3.24.1

WORKDIR /app
RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S shuuen \
    && adduser -S -G shuuen shuuen \
    && mkdir -p /app/data \
    && chown -R shuuen:shuuen /app

COPY --from=build --chown=shuuen:shuuen /out/shuuen-api /app/shuuen-api

USER shuuen

EXPOSE 9999
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:9999/healthz >/dev/null || exit 1
CMD ["/app/shuuen-api"]
