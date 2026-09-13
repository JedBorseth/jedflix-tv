# Hosted 15s trailer clips for catalog preview

TMDB `/{movie|tv}/{id}/videos` identifies a YouTube trailer. It does not return a file ExoPlayer can play. Catalog trailer preview therefore plays a progressive MP4 served by JedFlix's TV API, named `{youtubeKey}.mp4`, using a dedicated preview player — not the Real-Debrid `PlayerScreen`.

The app waits 1s after poster focus before fetching TMDB videos or preparing ExoPlayer, so D-pad scrolling does not start decoder/network work. After a 5s hold it morphs the focused poster in-shelf and plays. Quality profile Low disables this. A missing clip (404) is silent; the still catalog stays put.

Clips are 15 seconds, taken from about a third of the way into the source trailer. Generation is described in [0004](0004-clip-generation-on-disk1.md).
