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
