# Dynamic Home: model and catalog research

Initial research on 2026-10-06 used model-owner cards, inference code, TMDB documentation, and read-only live server inspection. The findings below describe the pre-implementation comparison. The approved implementation uses a dedicated TV service, independent of music AI; see [current behavior](dynamic-home.md) and [validation results](dynamic-home-validation.md).

## Recommendation

Use **Qwen3-Embedding-0.6B** as the first upgrade candidate, with a broader cached TMDB catalog and explicit theme queries. Benchmark it against the existing BGE-small and, if needed, BGE-large. It supports custom retrieval instructions and adjustable output dimensions. An embedding model retrieves supplied catalog entries; it does not generate titles or supply missing catalog coverage. [Qwen model card](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B)

The current [engine](../server/recommender/engine.py) uses BGE-small, up to 300 client candidates, and recommendations from two watched seeds. The [encoder](../server/recommender/model.py) runs ONNX on CPU. Upgrading that encoder alone will still rank the same narrow pool. Separate candidate acquisition, title encoding, theme eligibility, and personalized ranking. Existing title metadata, viewing signals, release rules, and response shape can mostly survive the change.

## Models within the approximate 3 GB budget

The weight figures below are **calculations**, using rounded parameter counts and bytes per parameter, rather than measured resident or peak VRAM. CUDA context, allocator reserve, activations, padding, quantization metadata, and temporary outputs are additional.

| Model | Documented limits | Calculated weight baseline | Assessment |
| --- | --- | --- | --- |
| Qwen3-Embedding-0.6B | 32K tokens; 32–1024 dimensions; custom instructions | About 1.2 GB at FP16 | First candidate. Bound context and batches so runtime has headroom. [Card](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B) |
| Qwen3-Embedding-4B | 32K tokens; up to 2560 dimensions | About 8 GB FP16; idealized 4-bit arithmetic about 2 GB | A quantization experiment rather than the default under 3 GB. Not every tensor is quantized; quality and speed must be rechecked. [Card](https://huggingface.co/Qwen/Qwen3-Embedding-4B), [quantization docs](https://huggingface.co/docs/transformers/quantization/bitsandbytes) |
| EmbeddingGemma-300M | 2048 tokens; 768/512/256/128 dimensions | About 0.6 GB BF16 or 1.2 GB FP32 | Good compact comparison, especially with native BF16 hardware. **FP16 activations are unsupported**, so its runtime needs different configuration from Qwen. [Google card](https://huggingface.co/google/embeddinggemma-300m) |
| BGE-M3 | 8192 tokens; 1024 dimensions; dense, sparse and multi-vector retrieval | Roughly 1.1–1.2 GB FP16 using its approximately 0.6B size | Useful if multilingual/hybrid search becomes a requirement. Its query convention does not require instructions. [Card](https://huggingface.co/BAAI/bge-m3) |
| BGE-base-en-v1.5 | 512 tokens; 768 dimensions | About 0.22 GB FP16, calculated from the published 438 MB FP32 file | Conservative speed/compatibility comparison. [Card](https://huggingface.co/BAAI/bge-base-en-v1.5), [files](https://huggingface.co/BAAI/bge-base-en-v1.5/tree/main) |
| BGE-large-en-v1.5 | 512 tokens; 1024 dimensions | About 0.67 GB FP16, calculated from the published 1.34 GB FP32 file | Another efficient upgrade candidate; benchmark with our plots and themes. [Card](https://huggingface.co/BAAI/bge-large-en-v1.5), [files](https://huggingface.co/BAAI/bge-large-en-v1.5/tree/main) |

Qwen's card reports English MTEB v2 task means of 70.70 for 0.6B and 74.60 for 4B. These are general semantic/retrieval results, not movie recommendation or viewer-enjoyment measurements. Different task mixes, prompts, precision and evaluation dates make model-card score comparisons imperfect. Larger models are promising candidates, not proven improvements for JedFlix. [Qwen evaluations](https://huggingface.co/Qwen/Qwen3-Embedding-4B)

Start with 512-token documents, a batch of four, one GPU inference job at a time, FP16 on compatible NVIDIA hardware, inference mode, and no generation/KV cache. Compare batches 4 and 8 and context 256 and 512. These are proposed initial settings, not published performance guarantees. Only extend context when actual metadata loses important information.

## Prompt and vector conventions

Qwen retrieval queries use `Instruct: {task}\nQuery: {query}`. Titles use plain document text without that instruction; its reference implementation pools the final non-padding token and L2 normalizes. Example task: "Given a viewing theme, retrieve movie or television descriptions that match its mood and subject." A theme query should describe the content, such as festive Christmas celebrations and family reunions, rather than merely saying "Christmas movies." [Official implementation](https://github.com/QwenLM/Qwen3-Embedding)

EmbeddingGemma uses `task: search result | query: {content}` and `title: {title} | text: {content}`. Use the proper retrieval methods rather than copying Qwen formatting. BGE v1.5 recommends its fixed search instruction for queries and no passage instruction; BGE-M3 explicitly removes that requirement. [Google prompts](https://ai.google.dev/gemma/docs/embeddinggemma/model_card), [BGE guidance](https://huggingface.co/BAAI/bge-large-en-v1.5), [BGE-M3 guidance](https://huggingface.co/BAAI/bge-m3)

Proposed title documents retain overview, genres, keywords, and selected credits. Keep runtime, release status, limited-series status, country offers and watched exclusions as structured filters. Theme fit is a soft relevance signal; a snow scene alone must not admit a title to Christmas Movies. Warm the theme vectors once, retrieve from cached title vectors, then combine theme fit, profile interests, quality and diversity. Keep calendar eligibility deterministic and use the device's local timezone.

Fingerprint caches with model ID, immutable revision, input recipe, output dimension, pooling, normalization and inference precision. Rebuild incompatible title vectors and recalibrate the existing 0.88 interest separation, 0.65 negative similarity and 0.18 diversity coefficient. Never compare Qwen and BGE vectors, including when their dimensions happen to match.

## Existing server inference may be reusable

The sibling checkout's [Compose config](../../jedflix/docker-compose.yml) already configures a GPU `music-ai` service using Qwen3-Embedding-0.6B plus Qwen3-Reranker-0.6B, 512-dimensional embeddings and batches of eight. Its [implementation](../../jedflix/apps/music-ai/app.py) serializes GPU jobs. Reusing an already resident embedder could avoid another weight allocation.

Read-only live inspection confirmed the user's GTX 1660 Ti with 6144 MiB total VRAM, **5914 MiB already used** at the sampled moment. The inference process in `/mnt/disk1/workspace/.venv-infer` used 4012 MiB, `music-ai` used 1828 MiB, and XTTS used 70 MiB. GPU utilization was zero; idle utilization does not free resident models. These values are a point-in-time observation, not a capacity guarantee.

`music-ai` health reported an error: embedding loaded, reranker not loaded, with CUDA OOM during another 20 MiB allocation when only 13 MiB was available. Its embedding route rejects requests while overall readiness is false, so it cannot currently be reused or benchmarked despite its loaded embedder. The TV's BGE-small CPU recommender remains healthy. The approximately 3 GB GPU budget is therefore unavailable in the present workload; resolve existing residency and use an embedding-only healthy service before adding another model. No other service was stopped or changed.

The service accepts `/v1/embed` documents or queries, but provides no custom embedding instruction field. A preformatted theme instruction can be sent as document-mode text; do not also enable its built-in query prompt. Title embedding context is not explicitly capped. Its health result exposes neither an immutable model revision nor peak VRAM. Sharing the service needs version guarantees, bounded inputs, and measurement under simultaneous music workloads.

Standard FlashAttention-2 requires Ampere/Ada/Hopper; its maintainers point Turing hardware to a separate implementation. BF16 acceleration likewise needs suitable hardware, so on the documented 1660 Ti prefer a compatible FP16 Qwen/SDPA path and test rather than copying newer-GPU examples. [FlashAttention hardware requirements](https://github.com/Dao-AILab/flash-attention)

An optional reranker can improve theme relevance for only the top 20–40 candidates, asynchronously and cached. Qwen3-Reranker-0.6B uses instruction/query/document inputs and yes/no logits rather than open-ended title generation. Its published results rerank top-100 retrieved documents, so they do not prove our small-slate benefit. [Reranker card](https://huggingface.co/Qwen/Qwen3-Reranker-0.6B)

Do not assume two FP16 0.6B models plus runtime fit within 3 GB: weights alone total roughly 2.4 GB. The sibling reranker also computes all-position logits before slicing the last token. At batch eight, 1024 tokens, vocabulary 151,669 and two bytes per logit, that tensor alone could approach 2.5 GB (calculated, not measured). A verified last-token-only logits option or smaller batches is necessary before using it within this budget. Keep reranking optional until measured.

## Broader TMDB candidates

Use rotating, bounded Discover queries across genres, eras, popularity bands, vote-backed quality, theme keywords and selected people; add trending and a small amount of seed recommendations. Discover supports genre/keyword/cast/crew, runtime, date, rating and provider filters; it gives broader selection than only each seed's recommendation list. Movie and TV filters differ. [Movie Discover](https://developer.themoviedb.org/reference/discover-movie), [TV Discover](https://developer.themoviedb.org/reference/discover-tv)

Build and refresh that public metadata corpus in background, rather than enriching hundreds of new titles during a Home request. Preserve Canada-first offers with US fallback and existing release eligibility. Start with a measured few thousand titles spanning the useful themes; grow from observed gaps. A 10,000-title 512-dimensional float32 matrix is 20.48 MB of vector data (calculated), so exact CPU dot products may suffice before adopting a vector database.

The existing TMDB cache keys only the URL path. Discover queries must include sorted relevant parameters in their cache keys; otherwise genres, pages and themes collide. Rate-limit acquisition, honor 429 responses, and cache repeated enrichment. [TMDB rate limits](https://developer.themoviedb.org/docs/rate-limiting)

## Acceptance measurements

Compare the old and new models on exactly the same eligible candidates, then separately measure the wider catalog's benefit. Use the proposed themes and actual profile seeds for blind shelf comparisons. Record relevant titles in the top ten, false holiday matches, diversity, repeat rate, shelf fill and candidate coverage. Require factual holiday/runtime/series filters to pass independently of similarity scores.

Measure startup/model download separately from steady state: query-embedding p50/p95, title throughput, warm ranking latency, cold-title enrichment, end-to-end response latency, peak GPU allocated/reserved/process memory, and impact on current server jobs. Include long metadata and maximum batch cases. Proposed target: warm recommendation responses under one second p95, approximately 3 GB peak added model-process VRAM, and no GPU work required to display cached Home. These are acceptance goals to validate on the user's hardware, not claimed performance.

The Android client must accept new shelf IDs and refresh by the next local eligibility boundary while preserving independent rails and focus. Keep shelves stable during a visit, prewarm the next time window, and show cached data immediately. Dynamic shelves remain a separate reviewable feature from the embedding migration.
