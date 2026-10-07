package httpserver

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestRecommendationsProxyWhitelistsSignals(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/recommendations" {
			t.Errorf("unexpected upstream request: %s %s", r.Method, r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		if strings.Contains(string(body), "secret") || strings.Contains(string(body), "name") {
			t.Errorf("forwarded identity/credential: %s", body)
		}
		var decoded map[string]any
		if err := json.Unmarshal(body, &decoded); err != nil {
			t.Fatal(err)
		}
		if decoded["feedback"] == nil {
			t.Error("missing fields must become empty arrays")
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"model":"BAAI/bge-small-en-v1.5","shelves":[],"eligibleKeys":[],"refreshedAt":1}`))
	}))
	defer upstream.Close()
	router := New(Config{RecommendationsURL: upstream.URL}).Router()
	request := httptest.NewRequest(http.MethodPost, "/recommendations", strings.NewReader(`{"profileName":"secret name","apiKey":"secret","history":[{"tmdbId":1,"mediaType":"movie","watchedMs":300000,"name":"secret"}]}`))
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != 200 {
		t.Fatalf("status = %d: %s", response.Code, response.Body.String())
	}
	if response.Header().Get("Cache-Control") != "no-store" {
		t.Fatal("personalized response must not be publicly cached")
	}
}

func TestRecommendationsRejectsInvalidAndUnavailable(t *testing.T) {
	router := New(Config{RecommendationsURL: "http://127.0.0.1:1"}).Router()
	for _, body := range []string{`{"candidates":[{"id":-1,"mediaType":"movie"}]}`, `{"history":[{"tmdbId":1,"mediaType":"movie","watchedMs":-1}]}`, `{"feedback":[{"tmdbId":1,"mediaType":"movie","value":"bad"}]}`, `{} {}`, strings.Repeat(" ", maxRecommendationBody+1)} {
		response := httptest.NewRecorder()
		router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/recommendations", strings.NewReader(body)))
		if response.Code != 400 {
			t.Errorf("invalid body status = %d", response.Code)
		}
	}
	response := httptest.NewRecorder()
	New(Config{}).Router().ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/recommendations", strings.NewReader(`{}`)))
	if response.Code != 503 {
		t.Fatalf("disabled status = %d", response.Code)
	}
}

func TestRecommendationGateDoesNotBlockHealthOrClips(t *testing.T) {
	s := New(Config{RecommendationsURL: "http://127.0.0.1:1"})
	s.recommendationGate <- struct{}{}
	defer func() { <-s.recommendationGate }()
	for path, expected := range map[string]int{"/recommendations": 429, "/health": 200, "/clips/abcdefghijk.mp4": 404} {
		method := http.MethodGet
		if path == "/recommendations" {
			method = http.MethodPost
		}
		response := httptest.NewRecorder()
		s.Router().ServeHTTP(response, httptest.NewRequest(method, path, strings.NewReader(`{}`)))
		if response.Code != expected {
			t.Errorf("%s status = %d, want %d", path, response.Code, expected)
		}
	}
}

func TestRecommendationsMalformedUpstream(t *testing.T) {
	for _, body := range []string{"not JSON", strings.Repeat(" ", 2*maxRecommendationBody+1)} {
		upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte(body)) }))
		response := httptest.NewRecorder()
		New(Config{RecommendationsURL: upstream.URL}).Router().ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/recommendations", strings.NewReader(`{}`)))
		upstream.Close()
		if response.Code != 502 {
			t.Errorf("malformed upstream status = %d", response.Code)
		}
	}
}

func TestRecommendationsForwardsDeviceTimezoneAndLatestEpisodeOnly(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "" || r.Header.Get("X-Profile-ID") != "" {
			t.Error("identity or credential headers reached the recommendation worker")
		}
		body, _ := io.ReadAll(r.Body)
		if strings.Contains(string(body), "secret") {
			t.Errorf("unrecognized identity/credential fields were forwarded: %s", body)
		}
		var decoded recommendationRequest
		if err := json.Unmarshal(body, &decoded); err != nil {
			t.Fatal(err)
		}
		if decoded.TimeZone != "America/Vancouver" || len(decoded.History) != 1 {
			t.Fatalf("missing local context: %+v", decoded)
		}
		history := decoded.History[0]
		if history.Season != 2 || history.Episode != 8 || history.PositionMS != 96000 || history.DurationMS != 100000 || history.LatestWatchedMS != 75000 {
			t.Errorf("latest episode progress changed: %+v", history)
		}
		_, _ = w.Write([]byte(`{"model":"Qwen/Qwen3-Embedding-0.6B","shelves":[],"catalogReady":false,"validUntil":123,"modelVersion":"qwen3-v1"}`))
	}))
	defer upstream.Close()
	request := httptest.NewRequest(http.MethodPost, "/recommendations", strings.NewReader(`{
		"timeZone":"America/Vancouver", "profileId":"secret-profile", "apiKey":"secret-key",
		"history":[{"tmdbId":123,"mediaType":"tv","season":2,"episode":8,"watchedMs":300000,
		"positionMs":96000,"durationMs":100000,"latestWatchedMs":75000,"profileName":"secret-name","token":"secret-token"}],
		"candidates":[{"id":42,"mediaType":"movie","title":"Public title","deviceId":"secret-device"}]
	}`))
	request.Header.Set("Authorization", "Bearer secret-token")
	request.Header.Set("X-Profile-ID", "secret-profile")
	response := httptest.NewRecorder()
	New(Config{RecommendationsURL: upstream.URL}).Router().ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("status = %d: %s", response.Code, response.Body.String())
	}
	var payload map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &payload); err != nil {
		t.Fatal(err)
	}
	if payload["catalogReady"] != false || payload["validUntil"] != float64(123) || payload["modelVersion"] != "qwen3-v1" {
		t.Fatalf("worker freshness metadata was dropped: %v", payload)
	}
}

func TestRecommendationsRejectsInvalidDeviceTimezoneOrEpisodeBeforeProxying(t *testing.T) {
	requests := 0
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		_, _ = w.Write([]byte(`{}`))
	}))
	defer upstream.Close()
	router := New(Config{RecommendationsURL: upstream.URL}).Router()
	bodies := []string{
		`{"timeZone":"Mars/Unknown"}`, `{"timeZone":"../etc/passwd"}`, `{"timeZone":123}`,
		`{"timeZone":"` + strings.Repeat("a", 81) + `"}`,
		`{"history":[{"tmdbId":1,"mediaType":"tv","season":-1}]}`,
		`{"history":[{"tmdbId":1,"mediaType":"tv","episode":100001}]}`,
		`{"history":[{"tmdbId":1,"mediaType":"tv","season":true}]}`,
		`{"history":[{"tmdbId":1,"mediaType":"tv","episode":1.5}]}`,
		`{"history":[{"tmdbId":1,"mediaType":"tv","season":"2"}]}`,
		`{"history":[{"tmdbId":1,"mediaType":"tv","latestWatchedMs":-1}]}`,
	}
	for _, body := range bodies {
		response := httptest.NewRecorder()
		router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/recommendations", strings.NewReader(body)))
		if response.Code != http.StatusBadRequest {
			t.Errorf("invalid local context status = %d for %s", response.Code, body)
		}
	}
	if requests != 0 {
		t.Fatalf("%d invalid requests reached the worker", requests)
	}
}

func TestRecommendationsLegacyClientsDefaultToUTC(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var decoded recommendationRequest
		if err := json.NewDecoder(r.Body).Decode(&decoded); err != nil {
			t.Fatal(err)
		}
		if decoded.TimeZone != "UTC" || decoded.History == nil || decoded.Candidates == nil {
			t.Errorf("legacy defaults missing: %+v", decoded)
		}
		_, _ = w.Write([]byte(`{}`))
	}))
	defer upstream.Close()
	response := httptest.NewRecorder()
	New(Config{RecommendationsURL: upstream.URL}).Router().ServeHTTP(response,
		httptest.NewRequest(http.MethodPost, "/recommendations", strings.NewReader(`{}`)))
	if response.Code != http.StatusOK {
		t.Fatalf("legacy client status = %d", response.Code)
	}
}
