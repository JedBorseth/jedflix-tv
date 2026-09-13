# JedFlix TV API

Small Go service for the Android TV client. Separate from the JedFlix web backend
(`JedBorseth/jedflix`); it shares that stack's Linux box and Caddy.

Public URL: `https://borseth.ddns.net/tv-api/` (Caddy strips `/tv-api`).

| Route         | Response                                |
| ------------- | --------------------------------------- |
| `GET /health` | `{"status":"ok","version":"<git sha>"}` |

## Run locally

```bash
cd server
go test ./...
go run ./cmd/api            # http://127.0.0.1:8080/health
```

## Deploy

`.github/workflows/server.yml` runs on pushes to `main` that touch `server/`:

1. `go vet` + `go test`
2. Build and push `ghcr.io/jedborseth/jedflix-tv/api:<sha>` and `:latest`
3. SSH to the server, `docker compose pull && docker compose up -d`, wait for the
   container healthcheck

### One-time server setup

```bash
mkdir -p ~/jedflix-tv-api
# copy server/docker-compose.yml to ~/jedflix-tv-api/docker-compose.yml
docker network ls | grep jedflix_default   # created by the jedflix compose project
cd ~/jedflix-tv-api && docker compose pull && docker compose up -d
```

If the compose project in `~/jedflix` is not named `jedflix`, set `JEDFLIX_NETWORK`
in `~/jedflix-tv-api/.env` to the real network name.

### GitHub configuration (`JedBorseth/jedflix-tv`)

Secrets (environment `production`):

- `PROD_SSH_HOST`, `PROD_SSH_USER`, `PROD_SSH_KEY` — a dedicated deploy key on the Linux box.
  The deploy job logs into GHCR with `GITHUB_TOKEN`, so no long-lived pull token is required.

Optional variable: `TV_API_DIR` (default `/home/jedborseth/jedflix-tv-api`).

The `deploy` job uses the `production` environment; create it (no protection rules
required) if it does not exist.

### Caddy

`jedflix/deploy/Caddyfile` needs:

```
handle /tv-api/* {
    uri strip_prefix /tv-api
    reverse_proxy jedflix-tv-api:8080
}
```

That change ships with the next jedflix production deploy.
