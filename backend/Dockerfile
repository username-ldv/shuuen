FROM golang:1.26-alpine AS build

WORKDIR /src

COPY go.mod ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/shuuen-api ./cmd/api

FROM alpine:3.20

WORKDIR /app
RUN apk add --no-cache ca-certificates tzdata

COPY --from=build /out/shuuen-api /app/shuuen-api

EXPOSE 8080
CMD ["/app/shuuen-api"]
