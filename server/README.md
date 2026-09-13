# JedFlix TV API

Small Go service for the Android TV client. Separate from the JedFlix web backend
(`JedBorseth/jedflix`); it shares that stack's Linux box and Caddy.

Public URL: `https://borseth.ddns.net/tv-api/` (Caddy strips `/tv-api`).

| Route                         | Response                                                     |
| ----------------------------- | ------------------------------------------------------------ |
| `GET /health`                 | `{"status":"ok","version":"<git sha>"}`                      |
| `GET /clips/{youtubeKey}.mp4` | Progressive H.264 AAC MP4 (Range / 206). 404 if missing.     |

Clips are files on disk1, not in the image: `/mnt/disk1/jedflix/tv-clips/{youtubeKey}.mp4`.
The catalog preview plays `https://borseth.ddns.net/tv-api/clips/{youtubeKey}.mp4`.

`clipgen` is a one-shot job in the same image. By default it walks **every Home
catalog shelf** (Jed's Picks, provider rails, Trending Now, Popular, Top Rated,
genre discovers — two TMDB pages each, same as a catalog visit). Watch History
is local and is not pre-warmed. Titles are deduped across shelves. Each Title
is handled **one at a time**: pick any TMDB YouTube video (trailers first, then
teasers, then clips), cut 15s from ~1/3 in, encode to ≤5MB. A yt-dlp/ffmpeg
miss is logged and the job continues. Each run tees stdout to
`/mnt/disk1/jedflix/tv-clips/logs/clipgen-<timestamp>.log`.

## Run locally

```bash
cd server
go test ./...
CLIP_DIR=/tmp/tv-clips go run ./cmd/api            # http://127.0.0.1:8080/health
TMDB_API_KEY=… CLIP_DIR=/tmp/tv-clips go run ./cmd/clipgen --limit 1
```

## Deploy

`.github/workflows/server.yml` runs on pushes to `main` that touch `server/`:

1. `go vet` + `go test`
2. Build and push `ghcr.io/jedborseth/jedflix-tv/api:<sha>` and `:latest`
3. SSH to the server, `docker compose pull && docker compose up -d`, wait for the
   container healthcheck

`up -d` does **not** run clipgen (compose profile `clipgen`).

### One-time server setup

```bash
mkdir -p ~/jedflix-tv-api /mnt/disk1/jedflix/tv-clips/logs
# copy server/docker-compose.yml to ~/jedflix-tv-api/docker-compose.yml
# ~/jedflix-tv-api/.env (never git):
#   TMDB_API_KEY=<tmdb v3 key>
#   CLIP_HOST_DIR=/mnt/disk1/jedflix/tv-clips
docker network ls | grep jedflix_default   # created by the jedflix compose project
cd ~/jedflix-tv-api && docker compose pull && docker compose up -d
```

Smoke, then the full Home catalog:

```bash
cd ~/jedflix-tv-api
docker compose --profile clipgen run --rm clipgen --limit 1
docker compose --profile clipgen run --rm clipgen
```

Nightly (jedborseth crontab, server local time):

```
0 4 * * * cd /home/jedborseth/jedflix-tv-api && docker compose --profile clipgen run --rm clipgen
```

If the compose project in `~/jedflix` is not named `jedflix`, set `JEDFLIX_NETWORK`
in `~/jedflix-tv-api/.env` to the real network name.

CI only `pull && up -d`; it does not refresh `docker-compose.yml` on the box.
Copy that file again when the volume / clipgen service / env change.

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

That change ships with the next jedflix production deploy. Clip URLs share this
prefix; no extra Caddy handle is required.
