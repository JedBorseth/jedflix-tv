# Hosted 30s trailer clips for catalog preview

TMDB `/{movie|tv}/{id}/videos` identifies a YouTube trailer. It does not return a file ExoPlayer can play. Catalog trailer preview therefore plays a progressive MP4 served by JedFlix's backend, named `{youtubeKey}.mp4`, using a dedicated preview player — not the Real-Debrid `PlayerScreen`.

The app fetches TMDB videos on poster focus, prepares the clip during a 5s hold, then morphs the focused poster into the billboard and plays. Quality profile Low disables this. A missing clip (404) is silent; the still catalog stays put.
