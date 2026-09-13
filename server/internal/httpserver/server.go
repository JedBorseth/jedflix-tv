// Package httpserver wires the chi router for the JedFlix TV API.
//
// The router is the module's interface: callers (main, tests) get an
// http.Handler and never touch chi directly.
package httpserver

import (
	"encoding/json"
	"log"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
)

// Config is everything the server needs from its environment.
type Config struct {
	// Version is reported by GET /health so a deploy can be confirmed from the TV.
	Version string
}

// Server owns the router and the handlers behind it.
type Server struct {
	cfg Config
}

// New builds a Server; call Router to get the http.Handler.
func New(cfg Config) *Server {
	if cfg.Version == "" {
		cfg.Version = "dev"
	}
	return &Server{cfg: cfg}
}

// Router returns the fully wired http.Handler.
func (s *Server) Router() http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(requestLogger)
	r.Use(middleware.Recoverer)

	// The Android TV client is not a browser, but a same-host web client may
	// call this API later; read-only CORS is safe to allow broadly.
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{http.MethodGet, http.MethodHead, http.MethodOptions},
		AllowedHeaders:   []string{"Accept", "Content-Type"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	r.Get("/health", s.handleHealth)

	return r
}

type healthResponse struct {
	Status  string `json:"status"`
	Version string `json:"version"`
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	writeJSON(w, http.StatusOK, healthResponse{Status: "ok", Version: s.cfg.Version})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Printf("write json: %v", err)
	}
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (w *statusWriter) WriteHeader(code int) {
	w.status = code
	w.ResponseWriter.WriteHeader(code)
}

func requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(ww, r)
		log.Printf("%s %s %d %s", r.Method, r.URL.RequestURI(), ww.status, time.Since(start).Round(time.Millisecond))
	})
}
