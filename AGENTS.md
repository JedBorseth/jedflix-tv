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
- Generate trailer clips on the Linux box with clipgen (serial yt-dlp+ffmpeg job, logs on disk1); do not generate on the TV client or on poster-focus.
- On Play, auto-select a stream from the quality profile and current sort (top is better); do not open the picker. If playback fails, try the next best stream.

## Learned Workspace Facts

- JedFlix TV is an Android TV app; in-app updates are distributed from GitHub Releases.
- Local user settings, including the Real-Debrid API key, must persist across version updates.
- Player chrome auto-hides after 4s of idle with no remote input; Up/Down/Left/Right open it when hidden; a click pauses immediately. Controls are Play, skip back 10, skip forward 10, and Switch stream (opens the picker for the current title), with Play focused when chrome opens. Seek is on the progress bar: Left/Right scrub, OK toggles pause/play, and the scrub preview overlays other controls. Skip intro (IntroDB) auto-focuses only when chrome is closed; outro uses the existing play-next screen, auto-focused. Default audio is English, captions are toggleable, and the audio track selector labels FFmpeg-transcoded tracks such as DTS/DTS-HD. Comet is configured not to fetch DTS lossless (DTS-HD MA / DTS:X); those releases are also dropped locally so they never reach Play.
- Watch availability is Canada-first, with US only when a title has no Canada offer.
- The home billboard stays on Trending Now and is not driven by provider shelves.
- Home shelves include Crave, Apple TV, Paramount+, and Disney+ movie and TV rails, plus Jed's Picks from TMDB lists 8693449 (movies) and 8693452 (shows).
- Catalog shelves load a second TMDB page when focus reaches the 8th-last item; they do not wrap or loop at the end.
- Each catalog shelf and detail rail (cast, seasons, episodes, More like this) keeps its own horizontal scroll; a visit starts at the first item, returning in that visit restores the last focused item by identity, and unfocused rails stay parked.
- Billboard arrival focuses Play; the backdrop Ken Burns pans left to right then fades to the next Trending title; Right at the end of a shelf does nothing; Left from the first title opens nav; Back on Detail pops to that title, Back on open nav closes the drawer, and Back on Home with the drawer closed does not exit the app.
- Catalog shelves can be shown, hidden, and reordered in Settings; Trending Now, Continue Watching, My List, and Watch History cannot be hidden or moved. Trending Now is always the first Home rail. Settings shows a few shelves until Show all, and expanding keeps that control in view.
- Settings includes a quality profile (Max, Medium, Low). Play auto-selects a stream from Comet's sorted list using that profile (Medium: at least 1080p, movies under 30GB and shows under 1GB; Low: 720p). If the chosen stream fails to resolve or play, try the next one down the list. Low disables catalog trailer autoplay and is meant to disable high-res browse images. A focused poster autoplays a hosted 15s MP4 after 5s: prepare waits 1s so scrolling stays snappy, then the card morphs in-shelf to 16:9, neighbors shift, a transparent folded-J sits bottom-left, and after the clip the wide poster stays with a play icon until focus leaves. Trailer preview never opens on a Trending shelf. Clips are 15s from ~1/3 into a TMDB YouTube video (trailer preferred), ≤5MB, stored at `/mnt/disk1/jedflix/tv-clips/{youtubeKey}.mp4` and served at `{TRAILER_CLIP_BASE_URL}/{youtubeKey}.mp4`. clipgen warms the Home catalog, not only Jed's Picks.
- Stream search remaps TMDB episode numbers to scene/broadcast numbers when they differ, so the selected episode matches torrent listings.
