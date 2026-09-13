# Clip generation on disk1

The TV client only GETs progressive MP4s. `clipgen` (same image as the TV API) materializes those files on the Linux box:

- Path: `/mnt/disk1/jedflix/tv-clips/{youtubeKey}.mp4` (HDD, not the SSD, not the container).
- Serve: `GET https://borseth.ddns.net/tv-api/clips/{youtubeKey}.mp4` via the existing `/tv-api` Caddy handle. Range / 206. Invalid keys and missing files are 404.
- Window: 15s starting at ~1/3 of the YouTube trailer (shorter sources keep everything; an overrun takes the last 15s).
- Cap: 5MB. Encode H.264 + AAC with `+faststart`, 720p then 480p if over cap. Refuse to publish over 5MB.
- Job: serial, one Title at a time. A yt-dlp/ffmpeg miss logs `FAIL` (stdout and `tv-clips/logs/clipgen-<timestamp>.log`) and continues. Nightly cron re-tries. `--limit N` for smokes.
- Scope: the Home catalog (Jed's Picks, Crave/Apple TV/Paramount+/Disney+ rails, Trending Now, Popular, Top Rated, genre discovers). Two TMDB pages per paged shelf. Watch History is not pre-warmed. Titles are deduped across shelves.
- YouTube key pick matches the Android `TrailerPicker`: trailers, then teasers, then any remaining YouTube video on the TMDB title.

Do not generate on poster-focus; yt-dlp+ffmpeg cannot finish inside the 5s hold.
