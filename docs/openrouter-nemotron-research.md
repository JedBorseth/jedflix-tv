# OpenRouter Nemotron options for Home

Checked 2026-10-11 against OpenRouter's live public model and endpoint APIs, model pages, and the linked NVIDIA trial terms. This is research only; no inference request, app migration, or server change was made.

Subsequent authorized inference tests and the account's actual allowance are recorded in [Home and prompt evaluation](openrouter-nemotron-evaluation.md).

## Recommendation

Evaluate **`nvidia/nemotron-3-super-120b-a12b:free`** first for selecting and organizing supplied TMDB candidates into shelves. Its current NVIDIA endpoint advertises `response_format` and `structured_outputs`, useful for returning shelf names and ordered candidate IDs as JSON. It has 262,144 tokens of context and optional reasoning. The model's general 1M-context description does not describe this endpoint's actual limit. This is an integration recommendation, not measured evidence that it improves JedFlix recommendation quality. [Model page](https://openrouter.ai/nvidia/nemotron-3-super-120b-a12b:free), [live endpoint](https://openrouter.ai/api/v1/models/nvidia/nemotron-3-super-120b-a12b:free/endpoints)

**Do not make the free NVIDIA endpoint an ongoing shipped Home dependency under its currently linked terms.** The model page expressly applies NVIDIA's API Trial Terms. Sections 1.2 and 1.4 restrict access to trials/internal evaluation and exclude use of the API service or generated content in production without a separate subscription/service arrangement. Free pricing therefore does not establish permission for ongoing production use. A paid third-party provider is a possible no-monthly-subscription alternative, subject to that provider's terms. [NVIDIA trial terms](https://assets.ngc.nvidia.com/products/api-catalog/legal/NVIDIA%20API%20Trial%20Terms%20of%20Service.pdf)

## Currently available candidates

These are endpoint capabilities observed in the public APIs, not promises about future availability. All `:free` entries below currently list zero input/output token pricing. [Chat models API](https://openrouter.ai/api/v1/models), [embedding models API](https://openrouter.ai/api/v1/embeddings/models)

| Model ID | Context | Assessment |
| --- | --- | --- |
| `nvidia/nemotron-3-super-120b-a12b:free` | 262,144 | First shelf-curation evaluation; advertises JSON schema and optional reasoning. |
| `nvidia/nemotron-3-ultra-550b-a55b:free` | 1,000,000 | Larger quality comparison; current free endpoint lacks `response_format`/`structured_outputs`. JSON would need prompting and validation. |
| `nvidia/nemotron-3.5-lightning:free` | 1,000,000 | Smaller throughput-oriented comparison; current free endpoint also lacks schema parameters. |
| `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` | 256,000 | Multimodal focus is unnecessary for text candidate ranking; lacks schema parameters. |
| `nvidia/nemotron-3-embed-1b:free` | 32,768 per input | Closest encoder-only replacement for Qwen; returns vectors rather than curated shelves. |

The historical **`nvidia/nemotron-3-nano-30b-a3b:free`** did not appear in the live chat model list, and its endpoint API returned an empty provider list. Do not choose that older free slug from a stale guide. The free content-safety model is a moderation model rather than a recommender. [Nano endpoint](https://openrouter.ai/api/v1/models/nvidia/nemotron-3-nano-30b-a3b:free/endpoints), [current models](https://openrouter.ai/api/v1/models)

Schema support is provider-specific. For a future Super integration, request `response_format: {type: "json_schema", ...}` with `provider.require_parameters: true`; still validate candidate IDs, duplicates, eligibility, and output size locally. [Structured output documentation](https://openrouter.ai/docs/guides/features/structured-outputs)

## Cost and account limits

OpenRouter offers free API access without a recurring subscription. Its official pricing lists **50 requests/day** for a free account. Its official cost guide documents **20 requests/minute**, with the free-model daily ceiling raised to **1,000** after purchasing at least **$10 in credits**. This is a credit purchase, not a subscription; the daily limit is shared across free-model usage. The guide says failed requests count too. Check the authenticated key endpoint for the account's actual allowance before implementation. [Pricing](https://openrouter.ai/pricing), [official cost guide](https://openrouter.ai/blog/tutorials/how-to-get-the-lowest-cost-llm-inference-on-openrouter/), [limits](https://openrouter.ai/docs/api-reference/limits)

For ongoing use, the live paid **`nvidia/nemotron-3-super-120b-a12b`** endpoint lists DekaLLM at **$0.08/M input and $0.45/M output**, with schema support. A hypothetical 20,000-input-token plus 2,000-output-token refresh costs **$0.0025**; once daily for 30 days is **$0.075**, before credit purchase fees, retries, or additional reasoning/output tokens. These are calculated examples, not measured Home usage. Another listed provider has a different price and capability set. [Paid endpoints](https://openrouter.ai/api/v1/models/nvidia/nemotron-3-super-120b-a12b/endpoints)

## Data handling

The free Super, Ultra, and Embed pages expressly warn against submitting personal/confidential data and disclose NVIDIA logging for security and product/service improvement. The trial terms include improvement of AI models. These are not zero-retention endpoints. OpenRouter's own prompt storage is opt-in, but that does not override NVIDIA's handling. Send only public candidate metadata and an unidentifiable aggregate taste summary for an evaluation; avoid account/device identifiers or identifiable viewing records. [Super notice](https://openrouter.ai/nvidia/nemotron-3-super-120b-a12b:free), [Embed notice](https://openrouter.ai/nvidia/nemotron-3-embed-1b:free), [trial terms](https://assets.ngc.nvidia.com/products/api-catalog/legal/NVIDIA%20API%20Trial%20Terms%20of%20Service.pdf), [OpenRouter data collection](https://openrouter.ai/docs/guides/privacy/data-collection)

## Fit with JedFlix

The current [Home design](dynamic-home.md) uses cached TMDB metadata and local embeddings. An LLM shelf curator would be a behavior change: rank bounded real candidates and return their IDs, while JedFlix keeps release/theme eligibility, dislikes, identity, and availability deterministic. Refresh on the server when the taste profile or time window changes, save validated shelves, and serve them immediately from cache. A handful of daily refreshes should fit 50 requests/day for one household; this is a proposed request budget, not a tested workload. Benchmark relevance, diversity, invalid IDs, latency, and failure handling before replacing the current engine.

If the aim is only to remove local model hosting, **Nemotron 3 Embed 1B** is the closer architectural migration. It uses OpenRouter's embeddings API; title and query vectors would both need rebuilding with verified task formatting/dimensions, never mixed with existing Qwen vectors. Its free route carries the same NVIDIA trial/privacy restrictions. [Embedding model](https://openrouter.ai/nvidia/nemotron-3-embed-1b:free), [embeddings API](https://openrouter.ai/docs/api-reference/embeddings)
