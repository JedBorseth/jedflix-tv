# Canada watch region with US fallback

Provider Shelves query TMDB with `watch_region=CA`. If that catalog is empty, they retry `US`. Crave has no United States catalog, so Canada is the source of truth; United States is only a fallback when the provider does not exist in Canada. We do not merge regions, which would duplicate Titles and mix two storefronts.
