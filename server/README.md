# JedFlix TV API

Small Go service for the Android TV client. Separate from the JedFlix web backend
(`JedBorseth/jedflix`); it shares that stack's Linux box and Caddy.

Public URL: `https://borseth.ddns.net/tv-api/` (Caddy strips `/tv-api`).

| Route                         | Response                                                     |
| ----------------------------- | ------------------------------------------------------------ |
| `GET /health`                 | `{"status":"ok","version":"<git sha>"}`                      |
| `GET /clips/{youtubeKey}.mp4` | Progressive H.264 AAC MP4 (Range / 206). 404 if missing.     |
| `POST /recommendations` | Personalized shelves + verified public-release eligibility. |

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

`POST /recommendations` proxies to a dedicated NVIDIA GPU sidecar. The TV sends
bounded viewing signals, My List, feedback, catalog IDs and its IANA timezone.
The service returns `For You`, up to two `Because you watched …` shelves and up to
five relevant dynamic shelves. A fresh profile can receive calendar and general
Show discovery shelves without inventing personal taste. Trending Now still
owns the Billboard. See [all 27 dynamic shelves](../docs/dynamic-home.md).

The model is [Qwen3-Embedding-0.6B](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B),
pinned to `97b0c614be4d77ee51c0cef4e5f07c00f9eb65b3`. It runs FP16 on CUDA with
SDPA, four documents per batch, at most 512 tokens, 1024 dimensions, final-token
pooling and L2 normalization. It requires a real GPU and fails startup if model
loading or real inference fails. The allocator is capped at 2560 MiB, leaving
headroom for the CUDA context within a roughly 3 GiB total process target.
The TV service is independent of the unrelated music AI service.

A persistent public TMDB catalog warms in the background using paginated Discover
queries across genres, eras, vote-backed quality, runtime, limited series,
seasonal keywords and verified credits. The initial target is 3000 Titles
(`RECOMMENDER_CATALOG_SIZE`, capped at 10000). TMDB requests are paced and honor
429 Retry-After. Title enrichment and embeddings are never performed by a Home
request. The HTTP path reads the current in-memory snapshot and scores its
precomputed vectors on CPU. All 27 theme queries are encoded at startup;
person shelves reuse their generic query and enforce exact public credit IDs.

Multiple distinct taste anchors represent a profile's interests. Actual viewing
time, completion, recency, My List and likes adjust their weights. Brief starts
are ignored; legacy playback position remains weaker evidence. Dislikes suppress
the Title and reduce close semantic neighbors. Diversity scoring limits thematic
repetition, with occasional quality-weighted exploration in For You. Completed
Show triggers require the verified final episode and actual viewing time for
that episode, not just a seek or a completed earlier episode.

Client descriptions never become persistent cached content or embeddings.
Meaningfully watched Titles, My List and explicit feedback Titles are excluded
from discovery. Dynamic shelves reserve their eligible Titles before personal
shelves are assembled so a small holiday pool cannot disappear into For You.
All returned discovery shelves are deduplicated. Shelves need at least six
Titles and are omitted when factual or calendar requirements cannot be met.
Dynamic Movie shelves require at least 45 minutes of verified runtime. Theme-fit
admission runs before taste ranking so weak matches cannot pad a Shelf.

### Release eligibility

The server inspects TMDB's [typed public release dates](https://developer.themoviedb.org/reference/movie-release-dates):
premieres don't count. Future public releases are rejected, including titles with
provider metadata but no public release yet. Movies released publicly in another
country are allowed to use that earlier date even when the Canadian primary date
is later. Theatrical-only movies wait 30 days from first public release; an already
released digital, physical or TV release, or a current home offer, can bypass that
holdback. [Watch providers](https://developer.themoviedb.org/reference/movie-watch-providers)
use Canada first and US only when no Canadian home offer exists. This is an
availability signal, **not proof of a playable Comet/Real-Debrid stream**. The
service never receives a Real-Debrid key or performs account-specific searches.
At least ten seconds of actual playback reported within the last seven days is
also account-local evidence of a usable stream: it bypasses the theatrical
holdback only after a public release. Old resume position, stale observations,
and festival premieres never bypass eligibility. These playback observations
are recomputed from the request and aren't saved as global availability.
Shows require an already reached first-air date, without the theatrical holdback.
Unknown release dates fail closed for recommendations; failed metadata lookups
are not declared ineligible for existing catalog shelves.

### Wire contract and bounds

Request:

```json
{
  "timeZone": "America/Vancouver",
  "history": [{"tmdbId": 550, "mediaType": "movie", "watchedMs": 6000000,
    "positionMs": 6000000, "durationMs": 8000000, "lastWatchedAt": 1791158400000,
    "season": 0, "episode": 0, "latestWatchedMs": 6000000}],
  "myList": [{"tmdbId": 680, "mediaType": "movie"}],
  "feedback": [{"tmdbId": 13, "mediaType": "movie", "value": "like", "updatedAt": 1791158400000}],
  "candidates": [{"id": 603, "mediaType": "movie", "title": "The Matrix",
    "overview": "…", "posterUrl": null, "backdropUrl": null,
    "year": 1999, "rating": 8.2, "genres": ["Science Fiction"], "releaseDate": "1999-03-30"}]
}
```

Response has `model`, `modelRevision`, `shelves: [{id,title,items}]`, `eligibleKeys`,
`evaluatedKeys`, `publicReleaseDates`, `completeEligibility`, `refreshedAt`, `validUntil`, `catalogReady`, and `modelVersion`
(milliseconds UTC). Keys are `movie-550` / `tv-1399`. Shelf items have the same
candidate shape, enriched from TMDB; `year` is numeric. `publicReleaseDates` maps
evaluated keys to earliest public dates. Clients must distinguish a failed lookup
from an authoritative rejection: only `evaluatedKeys - eligibleKeys` are known
ineligible. `validUntil` is the next safe eligibility refresh time (milliseconds UTC).
`catalogReady=false` also signals missing profile seed embeddings; the TV retries
while background enrichment catches up. Extra fields support partial updates.

Bodies are capped at 1 MiB; candidates at 300; each signal list at 200. The Go
route strips unknown fields and only forwards typed public context/activity,
never profile names, credentials or device identifiers. One active request
prevents overlapping page assembly; busy returns 429 + Retry-After 30. The TV
shows cached Home immediately and refreshes in the background. Failed requests
retain personal shelves, but expired calendar shelves are removed on the next
safe Home visit. Network updates cannot reshuffle a focused shelf.

Requests and profiles are never persisted or logged. Public TMDB metadata
(24-hour TTL) and embeddings live in SQLite on disk1. Vector cache identity
includes model revision, input recipe, dimensions, pooling and precision; BGE
vectors are rebuilt without touching watch history, profiles or credentials.
Response hashes/results remain in RAM only, up to 32 entries and ten minutes,
invalidated by catalog publication and the local eligibility boundary. The
sidecar has no published production port and shares only the private API network.
Docker limits it to two CPUs and 6 GiB of host RAM and grants NVIDIA GPU 0.

### Deploy and verify the model service

Provision the data directory once as its container UID (10001):

```bash
sudo mkdir -p /mnt/disk1/jedflix/tv-recommendations
sudo chown 10001:10001 /mnt/disk1/jedflix/tv-recommendations
# Existing ~/jedflix-tv-api/.env must contain a real nonempty TMDB_API_KEY.
```

The workflow tests Go and Python policy/ranking, builds both images, copies the
current compose file, and pins both images to the same `TV_API_TAG`. A normal
`docker compose up -d recommender api` keeps clipgen stopped. API health and clips
are independent of model readiness; recommendation requests fail gracefully if
the sidecar is unavailable. First startup downloads the pinned Qwen model and tokenizer (roughly 1.2 GB
of model weights); subsequent starts reuse disk1. NVIDIA Container Toolkit and
enough free VRAM must be available. Background catalog indexing survives
restarts through the public metadata/vector cache. Confirm both images
before testing the Android release:

```bash
cd ~/jedflix-tv-api
docker compose pull api recommender
docker compose up -d recommender api
docker inspect -f '{{.State.Health.Status}}' jedflix-tv-recommender
docker exec jedflix-tv-api curl -fsS http://recommender:8090/health
curl -fsS https://borseth.ddns.net/tv-api/health
# request.json should contain a known watched title + real catalog candidate IDs.
curl -fsS -H 'Content-Type: application/json' --data-binary @request.json \
  https://borseth.ddns.net/tv-api/recommendations
# Repeat to measure cached latency; verify shelves, no duplicates and release policy.
docker stats --no-stream jedflix-tv-recommender
```

Tests need no API key, downloaded model or GPU: unit tests inject deterministic
vectors only in test code. Production startup always loads and warms the real
Qwen GPU model, and refuses to start if the key/model is unavailable:

```bash
cd server/recommender
python3 -m unittest discover -v
# Optional actual model run on a CUDA-capable Linux host:
python3 -m venv /tmp/jedflix-recommender-venv
/tmp/jedflix-recommender-venv/bin/pip install -r requirements.txt
TMDB_API_KEY=… MODEL_CACHE_DIR=/tmp/jedflix-models CONTENT_CACHE_DB=/tmp/jedflix-content.sqlite3 \
  /tmp/jedflix-recommender-venv/bin/python service.py
```

Run the isolated model benchmark before starting the production GPU service:

```bash
# This loads a second model: stop ONLY the TV recommender first if it is running.
docker compose stop recommender
docker compose run --rm --no-deps --entrypoint python recommender benchmark_model.py
docker compose up -d recommender
nvidia-smi --query-compute-apps=pid,process_name,used_memory --format=csv
```

Record inference percentiles and total process VRAM separately from PyTorch
allocator memory. Also measure warm `/recommendations` latency with the real
public catalog; model-only timing does not cover the whole Home request.
