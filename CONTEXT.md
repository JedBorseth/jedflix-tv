# JedFlix TV

Android TV app for browsing and playing Movies and Shows.

## Language

**CatalogSection**:
One of the three catalog left-nav destinations: Home, Movies, or Shows. Live TV is a left-nav destination but not a CatalogSection.
_Avoid_: Category, tab, page

**Live TV**:
A left-nav destination that immediately tunes the last channel (Marvel on first use). Channels are looping queues of Titles. Tuning joins the current EPG program mid-title; after that the channel only advances when the current file ends. The TV guide is an in-player overlay.
_Avoid_: IPTV, linear TV (unless describing the EPG look), browse landing

**Channel**:
A named Live TV queue with a looping lineup (Marvel, 90s/2000s Cartoons, Comedy Central, Sitcom, Harry Potter, A&E, Sex and the City, Impractical Jokers, Discovery, HGTV, Food Network, Star Wars, Seinfeld, Disney XD, Brooklyn Nine-Nine, The Office 24/7, The Simpsons, Cartoon Network).
_Avoid_: station, network (unless it is the channel's name)

**Program**:
One Title (or episode) in a Channel lineup. The EPG shows it in 30-minute columns.
_Avoid_: slot (unless describing the 30-minute grid), broadcast item

**Guide**:
The in-player TV-guide overlay (channel rows × 30-minute columns) opened from a control next to Pause. It is the only way to change Channel.
_Avoid_: EPG screen, schedule page, channel picker landing

**Shelf**:
A named horizontal rail of Titles on a CatalogSection.
_Avoid_: Category, row, rail, collection, genre (unless it is actually a genre Shelf)

**Title**:
A Movie or a Show the viewer can open.
_Avoid_: Item, media item, MediaItem, catalog item

**Movie**:
A Title of type movie.
_Avoid_: Film

**Show**:
A Title of type TV.
_Avoid_: Series, TV show (as a type name), TV page

**Billboard**:
The hero at the top of a CatalogSection. It is driven by that section's Trending Shelf, not by editorial or Provider Shelves. On Home, Trending Now is the first Shelf. The backdrop Ken Burns pans left to right, then fades to the next Trending title.
_Avoid_: Banner, featured row

**Provider Shelf**:
A Shelf of Titles available on one streaming service (Crave, Apple TV, Paramount+, Disney+), split into Movies and Shows. Apple TV here is the subscription catalog, not the Apple TV Store.
_Avoid_: Watch provider row, OTT category

**Jed's Picks**:
Two editorial Shelves, Jed's Movies and Jed's Shows, whose Titles are the live contents of Jed's TMDB lists. Not a CatalogSection.
_Avoid_: Jed's list, curated category, hardcoded picks

**Trailer preview**:
A 15s hosted MP4 that morphs open from a focused poster after 5s on a CatalogSection, except on a Trending Shelf. The clip is taken from about a third of the way into a TMDB YouTube video (trailer if TMDB has one, otherwise teaser or any other YouTube clip). Clip prepare waits 1s after focus. Identity comes from TMDB videos; bytes come from `{TRAILER_CLIP_BASE_URL}/{youtubeKey}.mp4` (production: `https://borseth.ddns.net/tv-api/clips/{youtubeKey}.mp4`). When the clip ends the card stays 16:9 on the poster with a play icon until focus leaves. Off when the quality profile is Low.
_Avoid_: autoplay trailer, YouTube player, hero video (unless describing the billboard destination)
