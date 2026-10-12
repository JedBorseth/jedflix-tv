# JedFlix TV API

Small Go service for the Android TV client. Separate from the JedFlix web backend
(`JedBorseth/jedflix`); it shares that stack's Linux box and Caddy.

Public URL: `https://borseth.ddns.net/tv-api/` (Caddy strips `/tv-api`).

| Route                         | Response                                                     |
| ----------------------------- | ------------------------------------------------------------ |
| `GET /health`                 | `{"status":"ok","version":"<git sha>"}`                      |
| `GET /clips/{youtubeKey}.mp4` | Progressive H.264 AAC MP4 (Range / 206). 404 if missing.     |
| `POST /recommendations` | Cached personalized Home Shelves + verified public-release eligibility. |
| `POST /discover` | Live prompt-driven Title results. |

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
2. Build and push API and GPU recommender images at `<sha>` and `:latest`
3. Refresh compose on the server, pin `TV_API_TAG` to that SHA, pull and restart
   both services, and wait for API + real model healthchecks

`up -d` does **not** run clipgen (compose profile `clipgen`).

### One-time server setup

```bash
mkdir -p ~/jedflix-tv-api /mnt/disk1/jedflix/tv-clips/logs
sudo mkdir -p /mnt/disk1/jedflix/tv-recommendations
sudo chown 10001:10001 /mnt/disk1/jedflix/tv-recommendations
# copy server/docker-compose.yml to ~/jedflix-tv-api/docker-compose.yml
# ~/jedflix-tv-api/.env (never git):
#   TMDB_API_KEY=<tmdb v3 key>
#   OPENROUTER_API_KEY=<OpenRouter key>
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

CI refreshes `docker-compose.yml` before pulling both services. The model data
directory and real `TMDB_API_KEY` must be provisioned before the first deploy.

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

## Personalized discovery

`POST /recommendations` returns cached Home Shelves. `POST /discover` makes one
live OpenRouter request for a user-selected prompt and returns `{model,prompt,items}`.
Both routes use the same public TMDB catalog, taste references, and local release
policy. The model selects and orders actual TMDB Title keys; the server rejects
malformed structures, drops duplicate/out-of-pool selections, and never invents
replacement Titles.

The default is **`nvidia/nemotron-3-super-120b-a12b:free`**. The OpenRouter key stays
in `~/jedflix-tv-api/.env` on the Linux box, never in the APK or repo. There is no
self-hosted model, model download, PyTorch, CUDA or GPU reservation. The sidecar
uses Python's standard library, two CPUs, and a 512 MiB memory limit.

### Home cache and call allowance

A persistent SQLite Home plan contains model-selected Title keys, canonical
Shelf labels, generation time and model/prompt version. Each profile has a
random anonymous cache token persisted on the TV; names and device IDs are not
forwarded. The token stays out of model prompts. Raw viewing signals and raw
Search prompts are neither persisted nor logged on the server.

Home normally generates once per profile/local date when requested, subject to
a **global maximum of two Home generation attempts per Vancouver date** across
all profiles. The budget is reserved transactionally before the model call;
failed calls and process/container restarts do not reset it. A failed generation
has a one-hour per-token retry cooldown. Saved plans survive restarts and can
serve for up to seven days if a fresh generation fails or the daily allowance is
used. A profile with no prior plan waits for available generation capacity.

Each daily generation covers the date's eligible time windows. The server
activates the [existing 27 dynamic Shelf families](../docs/dynamic-home.md) from
the TV's current local time and viewing evidence, rechecks factual eligibility,
filters newly watched/disliked/library Titles, and deduplicates the displayed
page without another model call. Up to five dynamic Shelves appear alongside
For You and up to two Because you watched Shelves. The Billboard remains on
Trending Now. Client hourly/context refreshes are cached server reads.

Older app versions without a cache token use a digest of Title IDs and latest
feedback values. After an app update, a matching legacy plan can be adopted by
the new token without an additional generation.

Live Search prompt calls have **no response cache**. The persistent budget permits
up to 40 attempts per UTC date and up to 45 combined model attempts per UTC date,
leaving room below the account's 50-request tier. The client never starts prompt
inference from typing, focus, or screen arrival; one explicit button click makes
one request, with repeated clicks disabled while it is running. Before each
inference, the server also checks the key's actual shared free-model allowance;
Search stops with two requests reserved for Home. Provider 429s and unavailable
capacity fail gracefully. There are no automatic inference retries or paid
fallbacks, and provider pricing is capped at zero.

These budgets protect this installation, not arbitrary usage of the same key in
other apps; upstream quotas/capacity still apply. NVIDIA's free endpoint has its
own [trial/data-handling terms](../docs/openrouter-nemotron-research.md). An OpenRouter
credit purchase does not remove those terms.

### Public catalog and taste

The public catalog restores existing TMDB metadata immediately and warms in the
background using Discover pages across genres, eras, vote-backed quality, runtime,
limited Shows, seasonal keywords and verified credits. It targets 3000 Titles
(`RECOMMENDER_CATALOG_SIZE`, capped at 10000). TMDB requests are paced, respect 429
Retry-After, and public metadata persists with a 24-hour refresh policy. Home
reads the snapshot without TMDB network work; bounded metadata shortlists supply
coverage and the model makes the final selection/order.

Meaningful viewing time, completion, recency, My List and likes produce relative
taste references. Dislikes provide negative references. Only bounded public
Title descriptions and relative relevance enter the model prompt, without raw
viewing durations, timestamps, profile tokens or credentials. Prompt discovery
can expand public coverage for common moods through bounded TMDB keyword/genre queries;
enrichment is queued in the background so it cannot delay the model request. No model embeddings or local model scores are used.

### Release eligibility

Movies require an already reached public release; festival premieres do not
count. Fresh theatrical-only Movies wait 30 days unless a reached digital,
physical or TV release, a Canada-first home offer (US fallback), or recent actual
successful playback supports availability. Playback evidence never admits a
future public release. Shows require an already reached first-air date. Unknown
release dates fail closed. Availability is metadata evidence, not proof of a
playable Comet/Real-Debrid stream; the sidecar never receives the RD key.

### Wire contract and bounds

Both routes accept bounded `history`, `myList`, `feedback`, `candidates`, and
`timeZone`. Home also accepts an optional anonymous `cacheToken` (16–100 ASCII
letters, digits, underscores or hyphens). Discovery requires a nonblank `prompt`
of up to 500 characters. Bodies are capped at 1 MiB, candidates at 300, and each
signal list at 200. The Go proxy strips unknown identity/credential fields.

Home returns `model`, `modelVersion`, `shelves: [{id,title,items}]`,
`eligibleKeys`, `evaluatedKeys`, `publicReleaseDates`, `completeEligibility`,
`refreshedAt`, `generatedAt`, `validUntil` and `catalogReady`. Millisecond
timestamps are UTC. Keys are `movie-550` / `tv-1399`. Shelf/Discovery items use the
same enriched candidate shape as the TV. Only `evaluatedKeys - eligibleKeys`
establish authoritative ineligibility. `validUntil` is the next local eligibility
boundary, not the model generation deadline.

The TV shows its per-profile disk cache immediately and refreshes in the
background; expired calendar Shelves are removed safely. Updates wait while a
Shelf has focus. Cached Home and live Discovery have separate request gates;
busy/quota responses are 429 with Retry-After. HTTP responses are `no-store` so
intermediaries cannot share personalized results. Clips and health remain usable
if discovery is unavailable.

### Deploy and verify

Provision the persistent data directory for UID 10001, and real `TMDB_API_KEY`
and `OPENROUTER_API_KEY` in `~/jedflix-tv-api/.env`. The compose file defaults to
the explicit free Super model. CI tests Go/Python, builds both CPU-only images,
pins them to the pushed SHA and replaces the old GPU container. Existing public
metadata is restored; model weights and vector records from earlier versions
are unused.

```bash
cd server/recommender
python3 -m unittest discover -v
# Actual provider evaluation; credentials must come from the environment:
CONTENT_CACHE_DB=/tmp/jedflix-content.sqlite3 python3 service.py
```

```bash
cd ~/jedflix-tv-api
docker compose pull api recommender
docker compose up -d recommender api
docker exec jedflix-tv-api curl -fsS http://recommender:8090/health
curl -fsS https://borseth.ddns.net/tv-api/health
# request.json contains bounded signals, timeZone and an anonymous cacheToken.
curl -fsS -H 'Content-Type: application/json' --data-binary @request.json \
  https://borseth.ddns.net/tv-api/recommendations
# Repeat: generatedAt must stay constant and no new model attempt should be reserved.
docker stats --no-stream jedflix-tv-recommender
```

Unit tests inject dependencies without making paid/free inference requests.
Release validation additionally uses the real OpenRouter model and the local
Android TV emulator.
