## Agent skills

### Issue tracker

GitHub Issues for JedBorseth/jedflix-tv via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default roles mapped 1:1: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Learned User Preferences

- When playback or Real-Debrid work needs an API key, ask the user; do not add mock or workaround paths for a missing key.
- Ship app versions by pushing `main` and publishing GitHub Releases, not by leaving release work unfinished.
- Verify Android TV changes on the local emulator; do not treat untested cloud-agent diffs as ready.
- Do not intercept D-pad Up/Down to scroll a shelf into view before focus; keep independent-rail focus as in 0.4.0.

## Learned Workspace Facts

- JedFlix TV is an Android TV app; in-app updates are distributed from GitHub Releases.
- Local user settings, including the Real-Debrid API key, must persist across version updates.
- Player chrome auto-hides after 4s of idle with no remote input; a click pauses immediately; controls are Play, skip back 10, and skip forward 10 with Play focused when chrome opens; default audio is English, captions are toggleable, and seek is on the progress bar rather than a center overlay.
- Watch availability is Canada-first, with US only when a title has no Canada offer.
- The home billboard stays on Trending Now and is not driven by provider shelves.
- Home shelves include Crave, Apple TV, Paramount+, and Disney+ movie and TV rails, plus Jed's Picks from TMDB lists 8693449 (movies) and 8693452 (shows).
- Catalog shelves load a second TMDB page when focus reaches the 8th-last item; they do not wrap or loop at the end.
- Each catalog shelf and detail rail (cast, seasons, episodes, More like this) keeps its own horizontal scroll; a visit starts at the first item, returning in that visit restores the last focused item by identity, and unfocused rails stay parked.
- Billboard arrival focuses Play; Right at the end of a shelf does nothing; Left from the first title opens nav; Back on Detail pops to that title, Back on open nav closes the drawer, and Back on Home with the drawer closed does not exit the app.
- Catalog shelves can be shown, hidden, and reordered in Settings; Watch History cannot be hidden or moved.
- Settings includes a quality profile (Max, Medium, Low); Low is meant to disable autoplay trailers and high-res browse images.
- Stream search remaps TMDB episode numbers to scene/broadcast numbers when they differ, so the selected episode matches torrent listings.
