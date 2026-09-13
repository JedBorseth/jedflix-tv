package httpserver

import (
	"net/http"
	"os"
	"path/filepath"

	"github.com/JedBorseth/jedflix-tv/server/internal/clips"
	"github.com/go-chi/chi/v5"
)

// handleClip serves one progressive MP4. Missing files, a disabled CLIP_DIR,
// and anything that is not `{youtubeKey}.mp4` are 404 — the TV client fails
// silent. http.ServeFile supplies Range / 206.
func (s *Server) handleClip(w http.ResponseWriter, r *http.Request) {
	if s.cfg.ClipDir == "" {
		http.NotFound(w, r)
		return
	}
	key, ok := clips.KeyFromFile(chi.URLParam(r, "file"))
	if !ok {
		http.NotFound(w, r)
		return
	}
	path := filepath.Join(s.cfg.ClipDir, clips.FileName(key))
	st, err := os.Stat(path)
	if err != nil || st.IsDir() {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "video/mp4")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	http.ServeFile(w, r, path)
}
