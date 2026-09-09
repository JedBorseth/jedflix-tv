# JedFlix TV

Android TV app for browsing and playing Movies and Shows.

## Language

**CatalogSection**:
One of the three left-nav destinations: Home, Movies, or Shows.
_Avoid_: Category, tab, page

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
The hero at the top of a CatalogSection. It is driven by that section's Trending Shelf, not by whichever Shelf happens to be first.
_Avoid_: Banner, featured row

**Provider Shelf**:
A Shelf of Titles available on one streaming service (Crave, Apple TV, Paramount+, Disney+), split into Movies and Shows. Apple TV here is the subscription catalog, not the Apple TV Store.
_Avoid_: Watch provider row, OTT category

**Jed's Picks**:
Two editorial Shelves, Jed's Movies and Jed's Shows, whose Titles are the live contents of Jed's TMDB lists. Not a CatalogSection.
_Avoid_: Jed's list, curated category, hardcoded picks
