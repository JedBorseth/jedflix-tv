package clips

// Home catalog matches CatalogShelves.forSection(HOME) in the TV app, minus
// Watch History (local, not a TMDB shelf). Paged shelves fetch two TMDB pages,
// the same amount the catalog loads on a visit.
func HomeShelves() []Shelf {
	const pages = 2
	provider := func(name, mediaType string, id int) Shelf {
		return Shelf{Name: name, Kind: ShelfProvider, MediaType: mediaType, ProviderID: id, Pages: pages}
	}
	discover := func(name string, genreID int) Shelf {
		return Shelf{Name: name, Kind: ShelfDiscover, MediaType: "movie", GenreID: genreID, Pages: pages, MinVotes: 100}
	}
	return []Shelf{
		{Name: "Jed's Movies", Kind: ShelfEditorial, MediaType: "movie", ListID: 8693449},
		{Name: "Jed's Shows", Kind: ShelfEditorial, MediaType: "tv", ListID: 8693452},
		provider("Crave Movies", "movie", 230),
		provider("Crave Shows", "tv", 230),
		provider("Apple TV Movies", "movie", 350),
		provider("Apple TV Shows", "tv", 350),
		provider("Paramount+ Movies", "movie", 531),
		provider("Paramount+ Shows", "tv", 531),
		provider("Disney+ Movies", "movie", 337),
		provider("Disney+ Shows", "tv", 337),
		{Name: "Trending Now", Kind: ShelfTrending, MediaType: "all", Pages: pages},
		{Name: "Popular Movies", Kind: ShelfMovieList, List: "popular", Pages: pages},
		{Name: "Popular TV", Kind: ShelfTvList, List: "popular", Pages: pages},
		{Name: "Top Rated Movies", Kind: ShelfMovieList, List: "top_rated", Pages: pages},
		{Name: "Top Rated TV", Kind: ShelfTvList, List: "top_rated", Pages: pages},
		discover("Action Movies", 28),
		discover("Comedy Movies", 35),
		discover("Horror Movies", 27),
		discover("Sci-Fi Movies", 878),
	}
}

// ShelfKind is how Titles are fetched from TMDB.
type ShelfKind string

const (
	ShelfEditorial ShelfKind = "editorial"
	ShelfTrending  ShelfKind = "trending"
	ShelfMovieList ShelfKind = "movie-list"
	ShelfTvList    ShelfKind = "tv-list"
	ShelfDiscover  ShelfKind = "discover"
	ShelfProvider  ShelfKind = "provider"
)

// Shelf is one Home catalog rail to warm.
type Shelf struct {
	Name       string
	Kind       ShelfKind
	MediaType  string // movie, tv, or all
	ListID     int    // editorial TMDB list
	List       string // popular, top_rated, …
	GenreID    int
	ProviderID int
	Pages      int // 0 = all pages (editorial); otherwise cap
	MinVotes   int
}

func (s Shelf) editorialList() ListSpec {
	return ListSpec{ID: s.ListID, MediaType: s.MediaType}
}
