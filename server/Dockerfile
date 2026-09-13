FROM golang:1.24-alpine AS builder
ARG VERSION=dev
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath \
    -ldflags="-s -w -X main.Version=${VERSION}" \
    -o /out/api ./cmd/api \
 && CGO_ENABLED=0 GOOS=linux go build -trimpath \
    -ldflags="-s -w -X main.Version=${VERSION}" \
    -o /out/clipgen ./cmd/clipgen

FROM alpine:3.20
RUN apk add --no-cache ca-certificates curl ffmpeg python3 nodejs \
    && curl -fsSL -o /usr/local/bin/yt-dlp \
        https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && adduser -D -H -u 10001 api
WORKDIR /app
COPY --from=builder /out/api /app/api
COPY --from=builder /out/clipgen /app/clipgen
USER api
EXPOSE 8080
ENV ADDR=:8080
ENV CLIP_DIR=/clips
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/health || exit 1
CMD ["/app/api"]
