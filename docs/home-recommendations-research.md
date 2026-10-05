# Better Home recommendations

Research dates: 2026-10-03–2026-10-04. This document records the pre-implementation findings and proposals. Version 0.7.0 implements the BGE-small ONNX service, Home deduplication/release filtering, profile feedback, and checkpoint-based actual viewing time; see [current implementation](../server/README.md#personalized-discovery). Model comparisons and physical low-end TV measurements remain future evaluation work. Primary sources are linked beside their claims.

## Recommendation

Start with **page-level duplicate removal plus a content-based hybrid recommender**: TMDB recommendations supply candidates, title metadata and a small self-hosted embedding model help rank them, and a final page assembler balances relevance with variety. This can support both **For You** and **Because you watched …** without training a model on a large population of users.

For the initial model comparison, test `BAAI/bge-small-en-v1.5` against `sentence-transformers/all-MiniLM-L6-v2`, with a TMDB-only baseline. BGE-small is the first prototype candidate because its longer input accommodates plot and metadata. That is an engineering preference, not evidence that it gives the best movie recommendations. Choose the winner using our actual profiles and catalog.

The duplicate problem needs its own fix. A better scoring model can still select the same appealing movie for every shelf. Netflix's description of homepage generation separates candidate groups, filtering, within-row ranking, and page assembly with additional deduplication; it also describes balancing diversity with a stable browsing experience. [Netflix: Learning a personalized homepage](https://netflixtechblog.com/learning-a-personalized-homepage-aa8ec670359a)

## What JedFlix already saves (verified in the working tree)

**Yes: watch history and My List are already scoped to individual profiles.** Room stores them in `jedflix.db`; the repository follows the active profile, and the app supports up to five profiles. These are local TV profiles, not server accounts or identities shared between devices. The current TV API exposes health and clips only. Sources: [database](../app/src/main/java/com/jedflix/tv/data/local/JedflixDatabase.kt), [profile-scoped repository](../app/src/main/java/com/jedflix/tv/data/library/RoomUserLibraryRepository.kt), [library interface](../app/src/main/java/com/jedflix/tv/data/library/UserLibraryRepository.kt), [server routes](../server/internal/httpserver/server.go).

Each playback record contains profile ID, Movie/Show type, TMDB ID, season/episode, position, duration, last-watched timestamp, and cached Title metadata. Playback saves approximately every ten seconds and on lifecycle/player transitions. Live TV is explicitly excluded. Records replace the previous snapshot for the same profile/Title/episode, so history grows across distinct Titles and episodes but does **not** retain separate viewing sessions or rewatch counts. The Watch History shelf collapses Show episodes to the most recently watched record for that Show; recommendation code should use the underlying episode records when assessing Show engagement. Sources: [entities](../app/src/main/java/com/jedflix/tv/data/local/Entities.kt), [upsert and ordering](../app/src/main/java/com/jedflix/tv/data/local/Daos.kt), [player persistence](../app/src/main/java/com/jedflix/tv/ui/player/PlayerViewModel.kt), [Watch History projection](../app/src/main/java/com/jedflix/tv/data/library/RoomUserLibraryRepository.kt).

This is enough to start personalized recommendations, with limitations:

- Saved position/duration is a rough engagement signal; seeking can move position without watching, and replaying can overwrite an earlier completed snapshot.
- My List is a useful intent signal, not proof that the viewer liked a Title. The stored `rating` is TMDB's vote average, **not a viewer rating**.
- There is no recommendation impression log, explicit like/dislike/not-interested feedback, viewing-session ledger, or library sync in this implementation. Local profile IDs are auto-incremented database keys and cannot identify the same person across TVs.
- Add accumulated playing time, completion events, session/source information (manual Play versus autoplay), and optional feedback for stronger learning. Poster focus and automatic trailer previews should not count as strong positive feedback. Preserve existing history with a non-destructive database migration; raw old snapshots cannot reconstruct missing events.

Sources: [metadata mapping](../app/src/main/java/com/jedflix/tv/data/tmdb/MediaTitle.kt), [snapshot model](../app/src/main/java/com/jedflix/tv/data/library/PlaybackProgress.kt), [database entities](../app/src/main/java/com/jedflix/tv/data/local/Entities.kt), [repository interface](../app/src/main/java/com/jedflix/tv/data/library/UserLibraryRepository.kt). Signal additions above are proposals, not current behavior.

## Why Home repeats Titles (verified in the working tree)

Home separately requests Trending, Jed's Picks, Provider Shelves, Popular, Top Rated, and genre Shelves. Discover defaults to `popularity.desc`, so several Shelves naturally pull from the same popular Titles. `mapPage` and pagination remove duplicates **within a Shelf**; catalog assembly and personal-shelf merging do not coordinate identities across Shelves. Title identity already exists as `mediaType + TMDB ID`, which avoids collisions between Movies and Shows with the same numeric ID. Sources: [shelf definitions](../app/src/main/java/com/jedflix/tv/data/tmdb/CatalogSection.kt), [API defaults](../app/src/main/java/com/jedflix/tv/data/tmdb/TmdbApi.kt), [catalog assembly and deduplication](../app/src/main/java/com/jedflix/tv/data/tmdb/TmdbRepository.kt), [personal shelves](../app/src/main/java/com/jedflix/tv/ui/home/CatalogViewModel.kt), [identity](../app/src/main/java/com/jedflix/tv/data/tmdb/MediaTitle.kt).

**A model alone will not fix that:** choosing the final Home page must allocate Titles across discovery Shelves. Keep Continue Watching, My List, and Watch History faithful to their purposes; their intentional overlap is different from repeated discovery suggestions. Fetch/backfill a deeper candidate pool before removing overlap, since client Shelves currently cap at two TMDB pages. Keep each visit's selected order stable and apply new rankings on the next visit/refresh; paging should append without reshuffling existing posters. That preserves focus by identity and independent horizontal scrolling. Sources: [paging constraints](../app/src/main/java/com/jedflix/tv/data/tmdb/ShelfPaging.kt), [focus requirements](../AGENTS.md). Allocation/backfill are proposed changes.

The existing Detail-page “More like this” already comes from TMDB's `recommendations` response. This is a practical starting candidate source for “Because you watched…”, although the current mapper limits it to twenty Titles and does not personalize it from the user's history. Sources: [detail loading](../app/src/main/java/com/jedflix/tv/data/tmdb/TmdbRepository.kt), [recommendation mapping](../app/src/main/java/com/jedflix/tv/data/tmdb/MediaTitle.kt).

Any implementation should preserve Trending Now as the first Home Shelf and the Billboard's source, plus Canada-first Provider Shelves with US fallback only when the Canadian catalog is empty. TMDB provider availability describes subscription catalogs; it does not verify that Comet/Real-Debrid can resolve a playable stream. Sources: [Billboard ADR](adr/0001-billboard-stays-on-trending.md), [watch-region ADR](adr/0002-canada-watch-region-with-us-fallback.md), [provider querying](../app/src/main/java/com/jedflix/tv/data/tmdb/TmdbRepository.kt), [playback resolver](../app/src/main/java/com/jedflix/tv/data/playback/PlaybackResolver.kt).

## What supplies the recommendations

**Candidates:** combine results from several sources instead of repeatedly sorting every category by popularity:

- Recent strongly watched or liked titles → TMDB recommendations.
- Metadata similarity → genres, plot keywords, director, selected cast, language and year.
- Embedding similarity → themes and plot descriptions, including titles outside the current popular provider pages.
- A smaller discovery pool → well-supported ratings, older titles, new releases and editorial picks.

TMDB exposes paginated `/movie/{id}/recommendations` and `/tv/{id}/recommendations`. They accept title IDs, language and page, not our profile history; personalization comes from choosing and weighting the seed titles ourselves. The movie `/similar` endpoint explicitly relies on genres and plot keywords, so it should be compared separately rather than assumed equivalent to recommendations. [Movie recommendations](https://developer.themoviedb.org/reference/movie-recommendations), [TV recommendations](https://developer.themoviedb.org/reference/tv-series-recommendations), [Similar movies](https://developer.themoviedb.org/reference/movie-similar)

Discover can broaden the candidate pool using genre, keyword, cast/crew, release date, vote count/average, provider and region filters. For provider availability use `watch_region`, not just the release-date `region` parameter. Watch-provider responses describe country-specific streaming/rental/purchase offers; they do not establish whether a Real-Debrid stream will resolve. Preserve the existing Canada-first rule. [Discover movies](https://developer.themoviedb.org/reference/discover-movie), [Region support](https://developer.themoviedb.org/docs/region-support), [Watch providers](https://developer.themoviedb.org/reference/movie-watch-providers)

**Ranking:** initially combine seed recommendation rank, semantic similarity, specific metadata overlap, recency/confidence of the viewing signal, and a modest quality prior. Normalize or rank-fuse different sources; cosine scores and TMDB popularity do not share a meaningful scale. Penalize dismissed and already completed titles in discovery shelves. Do not hard-exclude everything merely started: some entries are unfinished and belong in Continue Watching.

**Page assembly:** identify a title by `(mediaType, tmdbId)`. Reserve required rails, then allocate a title to its strongest eligible discovery shelf and refill the other shelves from their next candidates. Provider membership remains valid after filtering. Treat My List, Continue Watching and Watch History as utility rails whose overlap may be intentional; report discovery and utility overlap separately. Start with no duplicate titles among discovery shelves, allowing explicit exceptions only if the resulting pools are too thin.

Exact deduplication is distinct from variety: ten different Marvel films are still a repetitive slate. Use caps on franchise/director/topic dominance and a relevance–similarity penalty when choosing the next item. Maximal Marginal Relevance is a primary-source precedent for balancing relevance against redundancy; applying it to movies here is a proposal. [Goldstein and Carbonell: MMR](https://aclanthology.org/X98-1025/)

Fetch a larger candidate pool before filtering; removing duplicates from two short pages can leave empty rails. Keep the selected slate stable for a Home visit, including when returning from Detail. Append eligible items during pagination without relocating already focused cards. Refresh next visit or after an explicit refresh.

## Release eligibility: future Movies and fresh theatrical releases

Added 2026-10-04 for the user's requirement: do not recommend Movies that have not released, and suppress fresh theatrical-only releases without excluding every Movie still in theaters. **Release eligibility is a separate filter before personalized ranking.** AI taste matching cannot establish playback availability.

TMDB's per-Movie `release_dates` endpoint distinguishes premiere (1), limited theatrical (2), theatrical (3), digital (4), physical (5), and TV (6). Use those typed, country-specific dates rather than treating a festival premiere or one primary date as proof of a home-viewing release. [TMDB release dates](https://developer.themoviedb.org/reference/movie-release-dates)

Proposed policy, evaluated in this order:

1. **Future public release: hide.** If the known public release dates are all in the future and there is no credible earlier public release, exclude the Movie from Home discovery. A premiere alone should not qualify it. An upcoming trailer, popularity, votes, or a torrent filename must not override this.
2. **Already available for home viewing: allow.** A digital/physical/TV date that has passed, or a current streaming/rental/purchase offer, is enough for the metadata baseline. It can still be playing in theaters. These signals indicate distribution, not a guarantee that Comet will supply a working stream.
3. **Theatrical-only, recently released: normally hide; allow with stronger stream evidence.** Start by testing a 30-day holdback when neither home-release metadata nor a recent acceptable cached-stream observation exists. Thirty days is a proposed tuning parameter, not an industry-wide release window. A verified usable stream can admit an already publicly released Movie earlier.
4. **Older theatrical-only Movie: eligible for the baseline.** Age clears the temporary holdback; it does not prove a stream exists. This retains the user's desired chance to discover Movies with streams during a long theatrical run. The stronger version uses cached availability evidence to improve confidence or excludes a recent confirmed no-stream result.
5. **Missing/conflicting dates: unknown.** Recheck recent candidates; do not interpret unknown as unreleased or as playable. Keep uncertain new Movies out of prominent suggestions until evidence improves, while avoiding a digital-date requirement that would remove older established Movies.

| Evidence | Proposed Home result |
| --- | --- |
| First public release is next month; only a trailer/premiere exists | Hide |
| Theatrical release last week; no home-release or acceptable stream evidence | Hide |
| Movie is still in theaters; digital release has passed | Allow |
| Movie has been in theaters for weeks; acceptable cached stream observed recently | Allow |
| Older theatrical release; stream availability unknown | Eligible with lower confidence in the metadata baseline |

Evaluate release evidence across countries carefully: a future Canadian theatrical opening must not hide a Movie already digitally released elsewhere. Keep Canadian provider offers first and use US offers only when Canada has none, following the existing preference. Broader public-release evidence and a Canadian provider membership are different facts.

**Fast initial implementation:** retain full release dates, enrich recent Movies with typed release dates/offers, and apply the eligibility filter to every Home discovery source: Trending Now, its Billboard candidates, Provider Shelves, editorial suggestions, For You, and Because you watched. Trending still owns the Billboard. Filtering only Discover misses Movies arriving through Trending and recommendations. Backfill all filtered shelves. Do not automatically erase Titles from My List or Watch History.

Discover supports date bounds and release-type filtering; `region` selects release-date semantics while `watch_region` concerns provider offers. Multiple release-type order can affect the returned date, so separate theatrical/home-release candidate queries and validate typed dates rather than assuming one combined request identifies everything playable. [Discover filters](https://developer.themoviedb.org/reference/discover-movie), [Region semantics](https://developer.themoviedb.org/docs/region-support). Country-specific offers are available through TMDB's JustWatch-powered provider endpoint. [Watch-provider data](https://developer.themoviedb.org/reference/movie-watch-providers)

**Stronger availability evidence:** use successful playback observations and bounded background checks for recent, high-ranking candidate Movies. Cache evidence by Title and relevant debrid configuration, with observation time and `available / unavailable / unknown`; refresh recent releases more often. A timeout, expired credential, scraping notice, or provider outage is unknown, not a confirmed absence. A cached listing is weaker than a resolved candidate, and actual playback success is stronger still. Never block Home or perform stream searches on poster focus. Reuse observations before considering server-side probing; implementation would need actual authorized credentials.

An acceptable candidate should satisfy the current English, cached-stream and local DTS-lossless rules and exclude CAM/TS or other unwanted theatrical captures. A 1080p label alone does not establish source quality. Preserve Comet's best-first ordering for Play; Browse quality must not filter playback candidates. These requirements and the limits of hosted filtering are recorded in [Comet stream selection](comet-stream-selection.md); local candidate/resolve semantics are in [StreamOption](../app/src/main/java/com/jedflix/tv/data/comet/StreamOption.kt) and [CometClient](../app/src/main/java/com/jedflix/tv/data/comet/CometClient.kt).

**Current code gap:** list DTOs receive `release_date`, but `MediaTitle` keeps only the year. There are no typed release-date/provider-offer endpoints, Discover date parameters, or persistent Movie-availability cache in the audited TV implementation. Retain full dates and enrich centrally before filtering. If the same rule later covers the Movies destination, its explicitly “Coming Soon” shelf would need removal from ordinary discovery. Sources: [DTOs](../app/src/main/java/com/jedflix/tv/data/tmdb/TmdbDtos.kt), [Title mapping](../app/src/main/java/com/jedflix/tv/data/tmdb/MediaTitle.kt), [TMDB API](../app/src/main/java/com/jedflix/tv/data/tmdb/TmdbApi.kt), [shelf definitions](../app/src/main/java/com/jedflix/tv/data/tmdb/CatalogSection.kt).

Compare the date/holdback baseline against the availability-enriched version using actual playback failures, eligible unique Titles, wrongly hidden playable Movies, and new-release freshness. The metadata baseline cannot guarantee that every displayed Movie is streamable. No live stream probes were run during this research.

## Small self-hosted models

An embedding model converts each title's text into a numeric vector. It does not chat, generate imaginary movie names, or need training on our users. Store vectors once per title; profile scoring compares them with watched-title vectors.

| Candidate | Documented size and limits | Fit for this experiment |
| --- | --- | --- |
| BGE-small-en-v1.5 | 33.4M parameters; 384 dimensions; 512 tokens; MIT; 133 MB safetensors weights | First prototype candidate; English metadata; use normalized vectors and follow its instruction guidance. |
| all-MiniLM-L6-v2 | 22.7M parameters; 384 dimensions; default truncation at 256 word pieces; Apache 2.0; 90.9 MB safetensors weights | Smaller comparison model; keep the metadata description concise. |
| E5-small-v2 | 33.4M parameters; 384 dimensions; 512 tokens; MIT; 133 MB safetensors weights | Optional third candidate; apply the documented prefixes, including `query:` for symmetric similarity. |

Sources: [BGE model card](https://huggingface.co/BAAI/bge-small-en-v1.5), [BGE files](https://huggingface.co/BAAI/bge-small-en-v1.5/tree/main), [MiniLM model card](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2), [MiniLM files](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/tree/main), [E5 model card](https://huggingface.co/intfloat/e5-small-v2), [E5 files](https://huggingface.co/intfloat/e5-small-v2/tree/main).

BGE documents CPU inference with ONNX; Sentence Transformers supports CPU, ONNX/OpenVINO and model quantization. A GPU is therefore not required for the proposed offline title-encoding job. The actual throughput and process memory must be measured on the Linux box; weight-file sizes are not RAM requirements. General embedding benchmarks measure retrieval/semantic tasks, not viewer enjoyment, and cannot prove which model is best here. [BGE CPU usage](https://huggingface.co/BAAI/bge-small-en-v1.5), [Sentence Transformers inference optimization](https://www.sbert.net/docs/sentence_transformer/usage/efficiency.html)

**Calculated storage example:** 10,000 titles × 384 dimensions × 4-byte float32 = 15.36 MB of raw vectors, or 153.6 MB for 100,000 titles. Metadata, IDs, database and index overhead are additional. At the smaller scale, benchmark exact vector comparisons before adding a vector database.

Proposed title text: concise overview, genres, keywords and selected creative credits. Keep structured metadata features separately so matching a director is intentional rather than an accident of text similarity. Version the model and input format; changing either requires rebuilding compatible vectors.

## Profiles and useful signals

Personalize per existing profile, not per installation or household. A profile shared by several people represents mixed preferences; multiple interest clusters can preserve those tastes better than one averaged vector. Movies and shows can share metadata while retaining separate output rails.

For **Because you watched …**, choose a recent title with meaningful viewing or explicit positive feedback, use it as the seed, and provide a coherent row. Avoid creating several rows from nearly identical seeds. For **For You**, blend candidates from multiple recent and longer-term interests with a modest exploration allocation. Keep source seed IDs for explanations.

Proposed signal priorities: explicit like/dislike or dismiss; meaningful completed viewing; repeated sustained viewing; My List additions; then weaker starts/details. Do not count a focus-triggered trailer preview as watching. Episode events should have diminishing contribution at show level so a binge does not drown out every other interest. Seeking near the end is not proof of completion; playback failures and interruptions are not reliable dislikes.

Cold start: optionally ask for a few liked titles, then use diverse editorial/popular picks until history grows. Netflix describes this onboarding approach, recent engagement weighting, viewing duration and ratings as personalization inputs. It is a useful precedent, not a requirement to copy their exact scoring. [Netflix recommendations explanation](https://help.netflix.com/en/node/100639)

If server-side personalization is chosen, add explicit profile identity and a small behavioral event feed. Store impressions only for cards actually visible, with shelf, rank, model version and recommendation reason, then attribute qualified starts and sustained viewing. Keep reset/delete controls tied to the profile. Alternatively, publish catalog vectors and perform profile scoring locally so watch history stays on the TV.

## Alternatives and when they become worthwhile

| Approach | Documented capability | Assessment for JedFlix |
| --- | --- | --- |
| TMDB-only + rules | Existing seed endpoints and discover filters | Best first baseline and quickest personalized shelves; semantic matching can be tested against it. |
| Small embedding hybrid | Pretrained title similarity without local model training | Recommended next experiment for sparse individual profiles; broader candidate coverage still has to be built. |
| Gorse | Item similarity using embeddings/tags/users; merged candidate sources and FM or LLM ranking; single-node binary with data/cache stores | Strong option if we want a complete recommender service/dashboard. More operational setup than a small scoring service. SQL can serve both store roles; Redis is not mandatory. |
| LightFM | Learns hybrid user/item representations from interactions and metadata | Consider after histories span enough users and overlapping titles to evaluate training. Metadata helps cold start, but training still needs useful interactions. |
| implicit ALS/BPR | Collaborative algorithms fitted to user–item confidence matrices, with multithreaded CPU training | Later collaborative baseline; sparse household-only data offers little evidence for cross-user learning. |

Sources: [Gorse item recommenders](https://gorse.io/docs/concepts/recommenders/item-to-item), [Gorse ranking](https://gorse.io/docs/concepts/ranking), [Gorse deployment](https://gorse.io/docs/deploy/binary), [LightFM docs](https://making.lyst.com/lightfm/docs/home.html), [implicit docs](https://benfred.github.io/implicit/). Assessments are inferences for our expected data size; no universal user-count threshold determines when collaborative training becomes useful.

A small generative model is an optional later reranker or metadata enrichment experiment. Constrain it to supplied candidate IDs, validate every result, cache outputs, and compare against deterministic ranking. Netflix's 2026 GenPage research reports successful generative homepages after pretraining on production pages and behavioral post-training. That supports the possibility of generative recommenders, but does not validate an off-the-shelf chat model for a few local histories. [GenPage, RecSys 2026](https://arxiv.org/abs/2606.31031)

## How to decide what produces better results

Compare sequentially: current shelves; dedup/refill only; TMDB personalized shelves; TMDB + metadata; embedding hybrid. Keep candidates and filters equal when comparing embedding models so a wider catalog is not mistaken for a better model.

For an offline comparison, hide each profile's later qualified watches and predict them from earlier signals. Use chronological splits, freeze title metadata to what was available then where feasible, and prevent same-show episode leakage. Record candidate recall separately from ranking NDCG/Recall@10, plus duplicate rate, unique titles, variety and rail fill. Evaluate on the real eligible pool rather than only a handful of sampled negatives. Gorse documents both online feedback rates and offline ranking metrics; its sampled leave-one-out evaluation is a useful reference, not sufficient proof of Home quality. [Gorse evaluation](https://gorse.io/docs/concepts/evaluation)

For a small household, pair metrics with a blind comparison of actual slates. Online outcomes should emphasize finding something worth watching: time to qualified playback, sustained viewing from recommendations, completion or explicit approval, and fewer dismissals. Trailer autoplay and incidental detail clicks can inflate engagement without improving selection. Few profiles and sessions will produce uncertain estimates; do not claim statistical superiority prematurely.

Cache bounded TMDB enrichment and seed results, refresh changed metadata and new releases, and back off on 429 responses. TMDB says its approximate upper rate limit can change. Do not assume a full-database crawl is needed. [TMDB rate limiting](https://developer.themoviedb.org/docs/rate-limiting)

Next implementation decision: first make release eligibility, duplicate removal and personalized TMDB baselines measurable, then prototype CPU embeddings on the Linux host. Server hardware, real profile-history volume and recommendation quality remain unmeasured. No deployment, application changes, live stream probes, or new history collection were performed for this research.

## Performance on older TVs

**Recommendation:** keep meaningful viewing time, but collect a compact session summary rather than an interaction firehose. Expected incremental cost is small with the design below; it has not been measured on an older TV, so “negligible” is a hypothesis, not a benchmark result.

The existing [PlayerViewModel](../app/src/main/java/com/jedflix/tv/ui/player/PlayerViewModel.kt) already updates its playback timeline every 250 ms and checkpoints progress every 10 seconds, with normal database writes dispatched to IO. It also saves when playback stops and during teardown. Teardown currently uses `runBlocking`, which still blocks its caller despite the inner IO dispatcher; do not add synchronous analytics there.

- Maintain only an accumulated duration and an active-playback start timestamp. Update them on `onIsPlayingChanged` and title/session transitions; count elapsed time only while actually playing. Use monotonic `elapsedRealtime`, initialize from current state, and close the active interval before release. Exclude previews; do not credit a seek jump as watched time. This requires no new polling timer, growing event list, or telemetry UI state. [Player events](https://developer.android.com/media/media3/exoplayer/listening-to-player-events), [SystemClock](https://developer.android.com/reference/android/os/SystemClock)
- Piggyback compact viewing-duration checkpoints on existing progress writes and summarize starts, duration, completion evidence and explicit feedback per session/profile. Avoid additional frequent writes: the current [DAO](../app/src/main/java/com/jedflix/tv/data/local/Daos.kt) observes the shared progress table, and Room reruns observable queries when any row in their table changes. Keep telemetry storage separate if it would otherwise trigger history/Home rebuilding; use asynchronous persistence without blocking teardown. [Room asynchronous queries](https://developer.android.com/training/data-storage/room/async-queries)
- Run embeddings and personalized ranking on the Linux server. Cache a bounded recommendation response on the TV, display it immediately, and refresh asynchronously between visits. Batch bounded summary uploads off playback, focus, and scrolling paths; skip impression/focus tracking initially.

Media3's `PlaybackStatsListener(keepHistory=false)` is an alternative if richer playback diagnostics are needed: it retains final counters without full event histories. Enabling full history introduces memory overhead that depends on session length and event count. A custom duration counter is narrower for this use case. [Media3 analytics](https://developer.android.com/media/media3/exoplayer/analytics)

Before rollout, compare frame timing, input response, CPU/memory and playback stability on a real low-end TV with collection enabled/disabled. If persistence or sync regresses playback, defer uploads and reduce checkpoints; existing history plus explicit feedback remains a useful fallback.

## Current Home performance inspection (2026-10-05)

The user's slowdown is on Home. The following are verified implementation details and optimization candidates, not a confirmed cause on their physical TV:

- Narrow posters still request the expanded trailer-card dimensions through `fitDp(PosterPreviewWidth, PosterHeight)`. Experiment with narrow decode sizing until a card expands; quantify cache/decode memory and avoid a second-load hitch during expansion. [Poster requests](../app/src/main/java/com/jedflix/tv/ui/components/PosterCard.kt)
- Low Browse quality requests smaller TMDB images and disables previews, but does not disable the Billboard pan or focused-poster glow. A less costly effects option would need an explicit design change to the existing animated-Billboard behavior. [Browse image sizes](../app/src/main/java/com/jedflix/tv/data/tmdb/BrowseImages.kt), [Billboard](../app/src/main/java/com/jedflix/tv/ui/components/Billboard.kt), [poster effects](../app/src/main/java/com/jedflix/tv/ui/components/PosterCard.kt)
- Release builds set `isMinifyEnabled = false`, disabling R8 optimization. Android recommends an R8-optimized release build for Compose performance. Any change needs release/playback/update validation; no speedup is established here. [Build configuration](../app/build.gradle.kts), [Android Compose performance](https://developer.android.com/develop/ui/compose/performance)
- Initial catalog display awaits all enabled shelf fetches. A slow lower shelf can delay initial Home availability; incremental shelf loading is a separate startup experiment. Existing shelves are lazy and use identity keys, and the Billboard pan pauses when scrolled away. [Catalog loading](../app/src/main/java/com/jedflix/tv/data/tmdb/TmdbRepository.kt), [Home layout](../app/src/main/java/com/jedflix/tv/ui/home/CatalogScreen.kt), [lazy shelves](../app/src/main/java/com/jedflix/tv/ui/components/CatalogRow.kt)

Local check: the installed 0.6.5 release was exercised using automated D-pad navigation and `adb shell dumpsys gfxinfo com.jedflix.tv framestats`. A temporary harness (`python3 /tmp/jedflix-home-perf.py`, removed after inspection) flagged navigation p95 above a 33ms diagnostic budget. Ten-second idle windows rendered 183 and 107 frames at the top of Home, versus zero frames in two settled lower-shelf windows. Zero-frame percentile values from gfxinfo are invalid and were excluded. This supports the render-demand distinction, not a causal attribution for the user's slowdown.

The emulator explicitly fell back to software graphics under host memory pressure. Absolute frame times are therefore unsuitable as physical-TV benchmarks, as [Android's profiling guide also explains](https://developer.android.com/codelabs/jetpack-compose-performance). No app code, focus behavior, settings, or release was changed. A real-TV comparison is needed to establish which optimization materially helps.
