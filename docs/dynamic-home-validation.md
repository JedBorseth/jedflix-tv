# Dynamic Home validation

Validated on 2026-10-06 (Vancouver) using the local Android TV emulator and the
actual Linux server's NVIDIA GTX 1660 Ti, 6 GiB. The dedicated TV service was
tested privately before replacing production. The unrelated music AI container
was neither changed nor restarted. The user-authorized large TTS process was
identified as `tts.server:app` and terminated to make VRAM available.

## Model measurements

Pinned Qwen3-Embedding-0.6B, FP16, 1024 dimensions, batches of four, 512-token cap;
ten iterations per input shape. Total process VRAM was sampled with nvidia-smi,
separately from PyTorch allocator statistics.

| Measurement | Median | p95 |
| --- | ---: | ---: |
| One short Title document | 68.41 ms | 90.82 ms |
| Four short Title documents | 268.46 ms | 274.07 ms |
| One theme query | 67.61 ms | 67.84 ms |
| Four maximum-length documents | 3043.25 ms | 3043.82 ms |

Peak total process VRAM was **1328 MiB**, with 1205 MiB peak allocator memory and
1236 MiB reserved. Initial startup including the pinned model download took
27.82 seconds. Embeddings had the expected dimensions and unit norm. Christmas
retrieval selected Home Alone over unrelated horror, sci-fi and workplace comedy
documents. Singleton/padded-batch cosine agreement was 0.999995.

The slowest encoding shape remains background work. Home page assembly uses
precomputed public Title/theme vectors on CPU and performs no model inference,
TMDB requests, or stream searches.

## Home response measurements

The first 25 uncached HTTP requests, while indexing continued, measured
48.3 ms p50, 54.9 ms p95 and 74.8 ms maximum against 215 indexed Titles. Payload
variations bypassed the in-memory response cache without changing taste signals.
No errors, duplicate Titles, missing posters, or release-ineligible results were
found. Each dynamic Shelf contained at least six Titles, and no more than five
dynamic Shelves appeared. Christmas was absent in October.

Actual title inspection prompted stronger semantic admission, a 45-minute
minimum for dynamic Movie shelves, exclusion of Christmas-only stories from
Spooky, and lighter comfort-shelf guards. After those changes, a further 25
uncached HTTP requests against 1196 indexed Titles measured **228.8 ms p50,
299.4 ms p95 and 322.1 ms maximum**, while background indexing continued.
All page and metadata guards passed, with zero errors. The TV GPU process stayed
at 1328 MiB in the sampled observations. This measured corpus was still warming
toward the 3000-Title target; these timings do not claim a full-corpus benchmark.
The final artifact is `home-final-results.json` in the server benchmark directory.
Halloween included The Conjuring, Halloween, Ghostbusters and Trick ’r Treat;
Spooky included Hocus Pocus, Monsters, Inc., Hotel Transylvania and Casper.

## Android and policy checks

- 241 Android unit tests passed, including profile/cache isolation, model
  migration, local-calendar/timezone/DST expiry and refresh scheduling.
- Three local emulator instrumentation tests passed, including update
  persistence and cumulative watch time with the latest episode's actual viewing.
- The signed 0.8.0 release APK built with R8 and passed Home navigation checks:
  51 D-pad inputs, no unintended playback, Detail/Back restored the same Title,
  and independent Shelves stayed focused.
- The emulator's measured UI frame p95 was 26 ms, with 2.01% current-definition
  janky frames. This is an emulator result, not a physical-TV guarantee.
- Go vet/tests passed. The Python suite covers all 27 activation families,
  release eligibility, broader Discover acquisition, semantic admission,
  whole-page deduplication, background-only encoding, bounded persistence and
  verified finale completion.
  All 65 policy/model-contract tests passed in the actual service image with
  zero skips, including real NumPy ranking and PyTorch pooling. The isolated
  tests used memory-backed temporary SQLite files; a prior container-overlay
  SQLite stall did not affect the persisted live catalog on disk1. CI installs
  the same pinned NumPy so it also exercises production ranking.

Raw server artifacts are under `/mnt/disk1/jedflix/tv-model-benchmarks/`.
Emulator navigation artifacts are under `/tmp/jedflix-dynamic-release-focus/`.
