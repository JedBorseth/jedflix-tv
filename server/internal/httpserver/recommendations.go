package httpserver

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"time"
	_ "time/tzdata"
)

const maxRecommendationBody = 1 << 20

type recommendationHistory struct {
	TMDBID          int    `json:"tmdbId"`
	MediaType       string `json:"mediaType"`
	WatchedMS       int64  `json:"watchedMs"`
	PositionMS      int64  `json:"positionMs"`
	DurationMS      int64  `json:"durationMs"`
	LastWatchedAt   int64  `json:"lastWatchedAt"`
	Season          int    `json:"season"`
	Episode         int    `json:"episode"`
	LatestWatchedMS int64  `json:"latestWatchedMs"`
}

type recommendationSignal struct {
	TMDBID    int    `json:"tmdbId"`
	MediaType string `json:"mediaType"`
	Value     string `json:"value,omitempty"`
	UpdatedAt int64  `json:"updatedAt,omitempty"`
}

type recommendationCandidate struct {
	ID          int      `json:"id"`
	MediaType   string   `json:"mediaType"`
	Title       string   `json:"title"`
	Overview    string   `json:"overview"`
	PosterURL   *string  `json:"posterUrl"`
	BackdropURL *string  `json:"backdropUrl"`
	Year        *int     `json:"year"`
	Rating      *float64 `json:"rating"`
	Genres      []string `json:"genres"`
	ReleaseDate *string  `json:"releaseDate"`
}

type recommendationRequest struct {
	TimeZone   string                    `json:"timeZone"`
	History    []recommendationHistory   `json:"history"`
	MyList     []recommendationSignal    `json:"myList"`
	Feedback   []recommendationSignal    `json:"feedback"`
	Candidates []recommendationCandidate `json:"candidates"`
}

func validTitle(id int, kind string) bool {
	return id > 0 && id < 1<<31 && (kind == "movie" || kind == "tv")
}

func (body recommendationRequest) valid() bool {
	if len(body.TimeZone) > 80 || body.TimeZone == "Local" {
		return false
	}
	if body.TimeZone != "" {
		if _, err := time.LoadLocation(body.TimeZone); err != nil {
			return false
		}
	}
	if len(body.Candidates) > 300 || len(body.History) > 200 || len(body.MyList) > 200 || len(body.Feedback) > 200 {
		return false
	}
	for _, row := range body.History {
		if !validTitle(row.TMDBID, row.MediaType) || row.WatchedMS < 0 || row.LatestWatchedMS < 0 || row.PositionMS < 0 || row.DurationMS < 0 || row.LastWatchedAt < 0 || row.Season < 0 || row.Season > 100000 || row.Episode < 0 || row.Episode > 100000 {
			return false
		}
	}
	for _, row := range body.MyList {
		if !validTitle(row.TMDBID, row.MediaType) {
			return false
		}
	}
	for _, row := range body.Feedback {
		if !validTitle(row.TMDBID, row.MediaType) || (row.Value != "like" && row.Value != "dislike") || row.UpdatedAt < 0 {
			return false
		}
	}
	for _, row := range body.Candidates {
		if !validTitle(row.ID, row.MediaType) || len(row.Title) > 8000 || len(row.Overview) > 8000 {
			return false
		}
	}
	return true
}

func (s *Server) handleRecommendations(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	if s.cfg.RecommendationsURL == "" {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "recommendations unavailable"})
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxRecommendationBody)
	var body recommendationRequest
	decoder := json.NewDecoder(r.Body)
	if err := decoder.Decode(&body); err != nil || !body.valid() {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid recommendation request"})
		return
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid recommendation request"})
		return
	}
	if body.History == nil {
		body.History = []recommendationHistory{}
	}
	if body.MyList == nil {
		body.MyList = []recommendationSignal{}
	}
	if body.Feedback == nil {
		body.Feedback = []recommendationSignal{}
	}
	if body.Candidates == nil {
		body.Candidates = []recommendationCandidate{}
	}
	if body.TimeZone == "" {
		body.TimeZone = "UTC"
	}
	// The public route permits one in-flight request; clips and health remain independent.
	select {
	case s.recommendationGate <- struct{}{}:
		defer func() { <-s.recommendationGate }()
	default:
		w.Header().Set("Retry-After", "30")
		writeJSON(w, http.StatusTooManyRequests, map[string]string{"error": "recommendation worker busy"})
		return
	}
	// Re-encoding a typed allowlist prevents accidental forwarding of names, credentials
	// or device identity. The sidecar independently enriches title content from TMDB.
	encoded, err := json.Marshal(body)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid recommendation request"})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 55*time.Second)
	defer cancel()
	upstream, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(s.cfg.RecommendationsURL, "/")+"/recommendations", bytes.NewReader(encoded))
	if err != nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "recommendations unavailable"})
		return
	}
	upstream.Header.Set("Content-Type", "application/json")
	client := s.cfg.RecommendationsHTTP
	if client == nil {
		client = &http.Client{Timeout: 55 * time.Second}
	}
	res, err := client.Do(upstream)
	if err != nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "recommendations temporarily unavailable"})
		return
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		status := http.StatusServiceUnavailable
		if res.StatusCode == http.StatusTooManyRequests {
			status = http.StatusTooManyRequests
			w.Header().Set("Retry-After", "30")
		}
		writeJSON(w, status, map[string]string{"error": "recommendations temporarily unavailable"})
		return
	}
	data, err := io.ReadAll(io.LimitReader(res.Body, 2*maxRecommendationBody+1))
	if err != nil || len(data) > 2*maxRecommendationBody || !json.Valid(data) {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": "invalid recommendation response"})
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(data)
}
