# Nemotron Home and prompt evaluation

Tested 2026-10-11 with the supplied OpenRouter key, using public TMDB metadata and a synthetic editorial brief. No real profile, viewing history, device identifier, or credentials were sent in model prompts. The key was passed to temporary probe processes through stdin and was not saved in the repository. These were initial probes; see [migration validation](openrouter-migration-validation.md) for the subsequent implementation and full-catalog checks.

## Account allowance

The authenticated `GET /api/v1/key` response reported a daily free-model ceiling of **50**, even though the account is no longer marked `is_free_tier`. `GET /api/v1/credits` reported **$5 purchased and $0 used** after testing. The key itself has a separate **$1 spending cap**; this is not the account's available balance. The last quota check reported 4 requests recorded and 46 remaining; five successful inference responses had arrived, so do not assume the counter settles synchronously.

OpenRouter documents 50 free-model requests/day and 20/minute below the higher credit tier; purchasing at least $10 in credits raises the daily allowance to 1,000, not unlimited. The daily allowance is shared across free models. Account limits do not guarantee that an upstream provider has capacity. [Official cost guide](https://openrouter.ai/blog/tutorials/how-to-get-the-lowest-cost-llm-inference-on-openrouter/), [limits and key endpoint](https://openrouter.ai/docs/api-reference/limits)

## Live inference results

All five calls used `nvidia/nemotron-3-super-120b-a12b:free`, routed to NVIDIA, and reported **$0 cost**. Each returned parseable schema-constrained JSON and used only supplied candidate IDs. These times measure HTTP completion for small fixtures, not production latency guarantees.

| Case | Candidates | Time | Findings |
| --- | --- | --- | --- |
| Initial dynamic Home | 57 | 2.86s | Two duplicate IDs across shelves; Monsters University (104 minutes in TMDB) incorrectly selected for the under-100-minute shelf. |
| Initial “silly movies that I would like” | 57 | 1.65s | Eight valid IDs; plausible but broad picks, including Shrek, The Hangover, Deadpool and Night at the Museum. |
| Home with factual eligibility pools | 65 | 1.91s | Three eight-title shelves; no invalid IDs, duplicates or tested eligibility violations. Some selections remained weak mood matches. |
| Prompt with expanded comedy candidates | 65 | 1.22s | Eight strong examples of silly comedy; no invalid or duplicate IDs. |
| Shuffled Home, clearer mood instructions, reasoning enabled | 65 | 8.97s | Three eight-title shelves; no tested eligibility violations. Used 1,023 reasoning tokens. Mood ranking still needs evaluation. |

The expanded prompt selected **Airplane!**, **The Naked Gun: From the Files of Police Squad!**, **Monty Python and the Holy Grail**, **Dumb and Dumber**, **Hot Fuzz**, **What We Do in the Shadows**, **Zoolander**, and **Austin Powers: International Man of Mystery**. Candidate expansion and prompt changes occurred together, so this is not an isolated model or prompt-quality comparison.

Initial candidates came from TMDB Discover comedy, family/fantasy, and horror/thriller results, enriched with real details/runtime. Eight specific comedy titles were added through TMDB Search and Details. This is a small, deliberately constructed pool, not representative coverage of the live catalog. [TMDB Discover](https://developer.themoviedb.org/reference/discover-movie), [Search](https://developer.themoviedb.org/reference/search-movie), [Details](https://developer.themoviedb.org/reference/movie-details)

For the stricter Home fixture, bedtime candidates had verified 45–99-minute runtime and no Horror/Thriller genre, and were reserved before the lighter shelf. Remaining lighter candidates had qualifying genres and no Horror/Thriller genre; the Halloween pool had verified Horror genre. These disjoint evaluation pools prevent overlaps. They do not replace the live application's fuller release, theme, taste, calendar, watched-title, dislike, and availability policy. Movie identity distinguishes the animated and live-action Beauty and the Beast, even when display titles coincide.

## Implications for implementation

Use the LLM to interpret a natural-language prompt and rank real candidate IDs, while the application owns factual eligibility and output validation. For Home, retain the [existing 27 category families and eligibility rules](dynamic-home.md), acquire a broad candidate pool, then rank and assemble cached shelves. Persist a daily plan and reapply current eligibility and feedback when serving it. Prompt discovery can use the same taste signals and ranking path, with broader retrieval guided by the prompt rather than limited to the current Home slate.

Do not call this proven personalization: the editorial brief was synthetic, no actual user signals were evaluated, and no comparison against the existing Qwen recommender was performed. JSON validity alone did not establish theme quality; the fast constrained Home response still included heavy titles such as The Wolf of Wall Street in After a Long Day. A representative quality evaluation should measure theme fit, preference fit, diversity, ordering stability, factual policy compliance, latency and failure behavior.

The free NVIDIA endpoint's [trial and data-handling restrictions](openrouter-nemotron-research.md#data-handling) still apply. Credit purchases and a valid key do not remove those provider terms. The [linked trial terms](https://assets.ngc.nvidia.com/products/api-catalog/legal/NVIDIA%20API%20Trial%20Terms%20of%20Service.pdf) restrict this endpoint to testing/evaluation; use a suitable production provider arrangement for an ongoing shipped dependency.
