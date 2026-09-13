FROM golang:1.24-alpine AS builder
ARG VERSION=dev
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath \
    -ldflags="-s -w -X main.Version=${VERSION}" \
    -o /api ./cmd/api

FROM alpine:3.20
RUN apk add --no-cache ca-certificates curl \
    && adduser -D -H -u 10001 api
WORKDIR /app
COPY --from=builder /api /app/api
USER api
EXPOSE 8080
ENV ADDR=:8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/health || exit 1
CMD ["/app/api"]
