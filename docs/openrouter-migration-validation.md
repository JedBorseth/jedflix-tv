# OpenRouter migration validation

Validated on 2026-10-11 for app 0.9.0, using
`nvidia/nemotron-3-super-120b-a12b:free`. Credentials remain server-side.

## Automated checks

- 63 Python tests cover persistent rankings, attempt budgets, concurrency,
  failure cooldown, restart reuse, current exclusions, calendar changes,
  legacy-client upgrades, expiry, live discovery and free-provider guards.
- Go tests and `go vet ./...` pass, including proxy endpoints and input bounds.
- 243 Android JVM tests pass. Debug and signed release APKs build;
  the release uses the production HTTPS API URL.

## Live model and emulator checks

The integration fixture used 3,034 public catalog records from the production
metadata cache. Real Home generation completed in 33.429 seconds. The displayed
response contained For You and five calendar/mood shelves; stored rankings also
covered the other times of day. After restarting both server processes, the
same-profile request took 0.235 seconds with identical `generatedAt` and no
additional model attempt.

Repeated silly-movie requests each made a fresh model call, taking 19.764 and
13.634 seconds. Results included Monty Python and Dumb and Dumber. This verifies
functionality; personalization quality has not been measured against a baseline.

The Android TV emulator verified initial prompt focus without the keyboard,
live A show to binge results, opening Detail and returning to results, and Back
to the prompt choices. Ordinary typing uses TMDB and consumes no model calls;
Down from the text field focuses the custom discovery button. Selecting the
custom action made exactly one live request, and Back from Detail restored the
selected second poster.

## Operational limits

Home allows at most two attempts per Vancouver calendar day across all profiles.
Failed attempts count, and successful plans persist across restarts. Current
watched/disliked titles and factual eligibility are filtered on each response.
Search allows 40 attempts per UTC day; combined calls have a ceiling of 45.
Search checks the account allowance to preserve two remaining requests for Home.
Valid stale Home plans remain usable for seven days after provider failures.

The account reports 50 free requests per day. See the [initial evaluation](openrouter-nemotron-evaluation.md)
for account details and provider terms. The CPU service replaces the TV GPU
model; other Linux-server workloads are outside this migration.
