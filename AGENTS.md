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
- On Play, auto-select a stream from Comet's best-first order; do not open the picker, including from autoplay or a Detail-page control. If playback fails, try the next best stream.
- On a Show Detail page, Browse Episodes focuses S1E2; the adjacent Play starts S1E1 or resumes from watch history.

## Learned Workspace Facts

- JedFlix TV is an Android TV app; in-app updates are distributed from GitHub Releases.
- Local user settings, including the Real-Debrid API key, must persist across version updates.
- Player chrome auto-hides after 4s of idle with no remote input; Up/Down/Left/Right open it when hidden; a click pauses immediately. Controls are Play, skip back 10, skip forward 10, and Switch stream (opens the picker for the current title), with Play focused when chrome opens. Seek is on the progress bar: Left/Right scrub, OK toggles pause/play, and the scrub preview overlays other controls. Skip intro (IntroDB) auto-focuses only when chrome is closed; outro uses the existing play-next screen, auto-focused. Default audio is English, captions are toggleable, and the audio track selector labels FFmpeg-transcoded tracks such as DTS/DTS-HD. DTS lossless (DTS-HD MA / DTS:X) releases are dropped locally so they never reach Play; hosted Comet may ignore the requested custom DTS exclusion. On movies (not shows), a Letterboxd control at the top-right opens a QR of `letterboxd://x-callback-url/log?name={title}`; the adjacent meatball does nothing.
- Watch availability is Canada-first, with US only when a title has no Canada offer.
- The home billboard stays on Trending Now and is not driven by provider shelves. Billboard arrival focuses Play; the backdrop Ken Burns pans left to right then fades to the next Trending title; Right at the end of a shelf does nothing; Left from the first title opens nav; Back on Detail pops to that title, Back on open nav closes the drawer, and Back on Home with the drawer closed does not exit the app.
- Home shelves include Crave, Apple TV, Paramount+, and Disney+ movie and TV rails, plus Jed's Picks from TMDB lists 8693449 (movies) and 8693452 (shows).
- Catalog shelves load a second TMDB page when focus reaches the 8th-last item; they do not wrap or loop at the end.
- Each catalog shelf and detail rail (cast, seasons, episodes, More like this) keeps its own horizontal scroll; a visit starts at the first item, returning in that visit restores the last focused item by identity, and unfocused rails stay parked.
- Catalog shelves can be shown, hidden, and reordered in Settings; Trending Now, Continue Watching, My List, and Watch History cannot be hidden or moved. Trending Now is always the first Home rail. Settings shows a few shelves until Show all, and expanding keeps that control in view.
- Settings includes Browse quality (Max, Medium, Low), which controls browse images and trailer previews only. Comet requests unlimited cached results per resolution, no size cap, and required English metadata. Play auto-selects the first stream in Comet's sorted list; browse quality never filters stream selection or fallbacks. See `docs/comet-stream-selection.md` when changing Comet filtering or ranking. If the chosen stream fails to resolve or play, try the next one down the list. Low disables catalog trailer autoplay and is meant to disable high-res browse images. A focused poster autoplays a hosted 15s MP4 after 5s: prepare waits 1s so scrolling stays snappy, then the card morphs in-shelf to 16:9, neighbors shift, a transparent folded-J sits bottom-left, and after the clip the wide poster stays with a play icon until focus leaves. Trailer preview never opens on a Trending shelf, and the clip plays only on the focused selected poster, not on other instances of the same title. Clips are 15s from ~1/3 into a TMDB YouTube video (trailer preferred), ≤5MB, stored at `/mnt/disk1/jedflix/tv-clips/{youtubeKey}.mp4` and served at `{TRAILER_CLIP_BASE_URL}/{youtubeKey}.mp4`. clipgen warms the Home catalog, not only Jed's Picks.
- Stream search remaps TMDB episode numbers to scene/broadcast numbers when they differ, so the selected episode matches torrent listings.
- Live TV is a left-nav destination (not a CatalogSection) that immediately tunes the last channel (Marvel on first use), persisted in SettingsStore. Tuning joins the current 30-min EPG program mid-title (clamped to the file); after that the channel is a queue that only advances when the file ends, next title from 0. The guide is an in-player overlay next to Pause (classic 30-min grid, video keeps playing, does not auto-hide) and is the only way to change channel; OK on another channel's now retunes, same-channel now closes the guide, future cells do nothing. Back closes the guide first, then leaves to Home. Live chrome keeps a non-interactive seek bar labeled Live that shows current program time; pausing is allowed. Titles that cannot resolve a stream are dropped from the guide and the channel advances to the next in lineup.
