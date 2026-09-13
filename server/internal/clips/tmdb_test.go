package clips

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestTMDBListTitlesPagesAndDefaultsMediaType(t *testing.T) {
	var pagesHit []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("api_key") != "k" {
			t.Errorf("missing api_key: %s", r.URL)
		}
		switch r.URL.Path {
		case "/list/42":
			page := r.URL.Query().Get("page")
			pagesHit = append(pagesHit, page)
			pageNum := 1
			if page == "2" {
				pageNum = 2
			}
			var items []map[string]any
			if page == "1" {
				items = []map[string]any{
					{"id": 1, "media_type": "movie", "title": "One"},
					{"id": 2, "name": "Two (no media_type)"},
				}
			} else {
				items = []map[string]any{{"id": 3, "media_type": "tv", "name": "Three"}}
			}
			_ = json.NewEncoder(w).Encode(map[string]any{"page": pageNum, "total_pages": 2, "item_count": 3, "items": items})
		case "/tv/3/videos":
			_ = json.NewEncoder(w).Encode(map[string]any{"results": []map[string]any{
				{"key": "abcdefg", "site": "YouTube", "type": "Trailer", "official": true, "iso_639_1": "en"},
			}})
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()

	tm := &TMDB{APIKey: "k", BaseURL: srv.URL}
	titles, err := tm.ListTitles(context.Background(), ListSpec{ID: 42, MediaType: "tv"})
	if err != nil {
		t.Fatal(err)
	}
	if len(titles) != 3 {
		t.Fatalf("titles %+v", titles)
	}
	if titles[0].MediaType != "movie" || titles[1].MediaType != "tv" || titles[1].Name != "Two (no media_type)" {
		t.Fatalf("titles %+v", titles)
	}
	if len(pagesHit) != 2 {
		t.Fatalf("pages hit %v", pagesHit)
	}

	videos, err := tm.Videos(context.Background(), "tv", 3)
	if err != nil {
		t.Fatal(err)
	}
	if PickYouTubeKey(videos) != "abcdefg" {
		t.Fatalf("videos %+v", videos)
	}
}

func TestTMDBPagedTrendingTwoPages(t *testing.T) {
	var pages []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/trending/all/week" {
			http.NotFound(w, r)
			return
		}
		page := r.URL.Query().Get("page")
		pages = append(pages, page)
		id := 10
		if page == "2" {
			id = 11
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"page":          id - 9,
			"total_pages":   5,
			"total_results": 100,
			"results": []map[string]any{
				{"id": id, "media_type": "movie", "title": "Hit"},
				{"id": 99, "media_type": "person", "name": "Skip me"},
			},
		})
	}))
	defer srv.Close()
	tm := &TMDB{APIKey: "k", BaseURL: srv.URL}
	got, err := tm.Titles(context.Background(), Shelf{Name: "Trending Now", Kind: ShelfTrending, MediaType: "all", Pages: 2})
	if err != nil {
		t.Fatal(err)
	}
	if len(pages) != 2 || len(got) != 2 || got[0].ID != 10 || got[1].ID != 11 {
		t.Fatalf("pages %v titles %+v", pages, got)
	}
}

func TestTMDBNon200IsError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, `{"status_message":"Invalid API key"}`, http.StatusUnauthorized)
	}))
	defer srv.Close()
	tm := &TMDB{APIKey: "bad", BaseURL: srv.URL}
	if _, err := tm.Videos(context.Background(), "movie", 1); err == nil {
		t.Fatal("want error")
	}
}
