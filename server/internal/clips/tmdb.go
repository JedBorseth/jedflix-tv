package clips

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

// Title is a Movie or Show from a TMDB list.
type Title struct {
	ID        int
	MediaType string // "movie" or "tv"
	Name      string
}

// ListSpec is one TMDB list to warm, with the media type its items are
// assumed to be when TMDB omits media_type.
type ListSpec struct {
	ID        int
	MediaType string
}

// ParseListSpecs reads "movie:8693449,tv:8693452".
func ParseListSpecs(s string) ([]ListSpec, error) {
	var specs []ListSpec
	for _, part := range strings.Split(s, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		kind, idStr, ok := strings.Cut(part, ":")
		if !ok || (kind != "movie" && kind != "tv") {
			return nil, fmt.Errorf("list spec %q must look like movie:ID or tv:ID", part)
		}
		id, err := strconv.Atoi(idStr)
		if err != nil || id <= 0 {
			return nil, fmt.Errorf("list spec %q has a bad id", part)
		}
		specs = append(specs, ListSpec{ID: id, MediaType: kind})
	}
	if len(specs) == 0 {
		return nil, fmt.Errorf("no lists given")
	}
	return specs, nil
}

// Fetcher is what the job needs from TMDB; tests substitute a fake.
type Fetcher interface {
	Titles(ctx context.Context, shelf Shelf) ([]Title, error)
	Videos(ctx context.Context, mediaType string, id int) ([]Video, error)
}

// TMDB talks to api.themoviedb.org v3 with an api_key query parameter.
type TMDB struct {
	APIKey  string
	BaseURL string // default https://api.themoviedb.org/3
	HTTP    *http.Client
}

func (t *TMDB) base() string {
	if t.BaseURL != "" {
		return strings.TrimRight(t.BaseURL, "/")
	}
	return "https://api.themoviedb.org/3"
}

func (t *TMDB) client() *http.Client {
	if t.HTTP != nil {
		return t.HTTP
	}
	return &http.Client{Timeout: 20 * time.Second}
}

func (t *TMDB) get(ctx context.Context, path string, query url.Values, out any) error {
	if query == nil {
		query = url.Values{}
	}
	query.Set("api_key", t.APIKey)
	u := t.base() + path + "?" + query.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "application/json")
	res, err := t.client().Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(res.Body, 512))
		return fmt.Errorf("tmdb %s: %s: %s", path, res.Status, strings.TrimSpace(string(body)))
	}
	return json.NewDecoder(res.Body).Decode(out)
}

type pagedResponse struct {
	Page         int `json:"page"`
	TotalPages   int `json:"total_pages"`
	TotalResults int `json:"total_results"`
	Results      []struct {
		ID        int    `json:"id"`
		MediaType string `json:"media_type"`
		Title     string `json:"title"`
		Name      string `json:"name"`
	} `json:"results"`
}

func titleFrom(id int, mediaType, fallbackType, title, name string) (Title, bool) {
	if id == 0 {
		return Title{}, false
	}
	mt := mediaType
	if mt != "movie" && mt != "tv" {
		mt = fallbackType
	}
	if mt != "movie" && mt != "tv" {
		return Title{}, false
	}
	n := title
	if n == "" {
		n = name
	}
	return Title{ID: id, MediaType: mt, Name: n}, true
}

// Titles fetches one Home shelf.
func (t *TMDB) Titles(ctx context.Context, shelf Shelf) ([]Title, error) {
	switch shelf.Kind {
	case ShelfEditorial:
		return t.ListTitles(ctx, shelf.editorialList())
	case ShelfTrending:
		return t.paged(ctx, "/trending/"+shelf.MediaType+"/week", url.Values{}, shelf.MediaType, shelf.Pages)
	case ShelfMovieList:
		return t.paged(ctx, "/movie/"+shelf.List, url.Values{}, "movie", shelf.Pages)
	case ShelfTvList:
		return t.paged(ctx, "/tv/"+shelf.List, url.Values{}, "tv", shelf.Pages)
	case ShelfDiscover:
		q := url.Values{
			"sort_by":       {"popularity.desc"},
			"include_adult": {"false"},
			"with_genres":   {strconv.Itoa(shelf.GenreID)},
		}
		if shelf.MinVotes > 0 {
			q.Set("vote_count.gte", strconv.Itoa(shelf.MinVotes))
		}
		return t.paged(ctx, "/discover/"+shelf.MediaType, q, shelf.MediaType, shelf.Pages)
	case ShelfProvider:
		return t.providerTitles(ctx, shelf)
	default:
		return nil, fmt.Errorf("unknown shelf kind %q", shelf.Kind)
	}
}

func (t *TMDB) providerTitles(ctx context.Context, shelf Shelf) ([]Title, error) {
	for _, region := range []string{"CA", "US"} {
		q := url.Values{
			"sort_by":              {"popularity.desc"},
			"include_adult":        {"false"},
			"with_watch_providers": {strconv.Itoa(shelf.ProviderID)},
			"watch_region":         {region},
		}
		got, err := t.paged(ctx, "/discover/"+shelf.MediaType, q, shelf.MediaType, shelf.Pages)
		if err != nil {
			return nil, err
		}
		if len(got) > 0 {
			return got, nil
		}
	}
	return nil, nil
}

func (t *TMDB) paged(ctx context.Context, path string, query url.Values, fallbackType string, maxPages int) ([]Title, error) {
	if maxPages <= 0 {
		maxPages = 2
	}
	var titles []Title
	seen := map[string]bool{}
	for page := 1; page <= maxPages; page++ {
		q := url.Values{}
		for k, vs := range query {
			q[k] = append([]string{}, vs...)
		}
		q.Set("page", strconv.Itoa(page))
		var res pagedResponse
		if err := t.get(ctx, path, q, &res); err != nil {
			return titles, err
		}
		added := 0
		for _, it := range res.Results {
			got, ok := titleFrom(it.ID, it.MediaType, fallbackType, it.Title, it.Name)
			if !ok {
				continue
			}
			key := got.MediaType + ":" + strconv.Itoa(got.ID)
			if seen[key] {
				continue
			}
			seen[key] = true
			titles = append(titles, got)
			added++
		}
		if added == 0 || len(res.Results) == 0 {
			break
		}
		if res.TotalPages > 0 && page >= res.TotalPages {
			break
		}
	}
	return titles, nil
}

type listResponse struct {
	Page       int `json:"page"`
	TotalPages int `json:"total_pages"`
	ItemCount  int `json:"item_count"`
	Items      []struct {
		ID        int    `json:"id"`
		MediaType string `json:"media_type"`
		Title     string `json:"title"`
		Name      string `json:"name"`
	} `json:"items"`
}

// ListTitles pages through a TMDB list. v3 lists return up to 20 items per
// page; paging stops when a page is empty, repeats, or passes item_count.
func (t *TMDB) ListTitles(ctx context.Context, list ListSpec) ([]Title, error) {
	var titles []Title
	seen := map[int]bool{}
	for page := 1; page <= 50; page++ {
		var res listResponse
		q := url.Values{"page": {strconv.Itoa(page)}}
		if err := t.get(ctx, "/list/"+strconv.Itoa(list.ID), q, &res); err != nil {
			return titles, err
		}
		added := 0
		for _, it := range res.Items {
			if it.ID == 0 || seen[it.ID] {
				continue
			}
			seen[it.ID] = true
			mt := it.MediaType
			if mt != "movie" && mt != "tv" {
				mt = list.MediaType
			}
			name := it.Title
			if name == "" {
				name = it.Name
			}
			titles = append(titles, Title{ID: it.ID, MediaType: mt, Name: name})
			added++
		}
		if added == 0 || len(res.Items) == 0 {
			break
		}
		if res.TotalPages > 0 && page >= res.TotalPages {
			break
		}
		if res.ItemCount > 0 && len(titles) >= res.ItemCount {
			break
		}
	}
	return titles, nil
}

// Videos returns TMDB's video list for a Title.
func (t *TMDB) Videos(ctx context.Context, mediaType string, id int) ([]Video, error) {
	var res struct {
		Results []Video `json:"results"`
	}
	path := fmt.Sprintf("/%s/%d/videos", mediaType, id)
	if err := t.get(ctx, path, nil, &res); err != nil {
		return nil, err
	}
	return res.Results, nil
}
