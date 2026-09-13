package httpserver

import (
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestClipServesMp4WithRange(t *testing.T) {
	dir := t.TempDir()
	body := make([]byte, 256)
	for i := range body {
		body[i] = byte(i)
	}
	if err := os.WriteFile(filepath.Join(dir, "dQw4w9WgXcQ.mp4"), body, 0o644); err != nil {
		t.Fatal(err)
	}
	h := New(Config{ClipDir: dir}).Router()

	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/clips/dQw4w9WgXcQ.mp4", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "video/mp4" {
		t.Fatalf("content-type = %q", ct)
	}
	if rec.Body.Len() != len(body) {
		t.Fatalf("body len %d", rec.Body.Len())
	}

	req := httptest.NewRequest(http.MethodGet, "/clips/dQw4w9WgXcQ.mp4", nil)
	req.Header.Set("Range", "bytes=0-9")
	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusPartialContent {
		t.Fatalf("range status = %d, want 206", rec.Code)
	}
	got, _ := io.ReadAll(rec.Body)
	if string(got) != string(body[:10]) {
		t.Fatalf("range body %v", got)
	}
}

func TestClipMissingAndInvalidAre404(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "secret.txt"), []byte("nope"), 0o644); err != nil {
		t.Fatal(err)
	}
	h := New(Config{ClipDir: dir}).Router()
	for _, path := range []string{
		"/clips/missingkey01.mp4",
		"/clips/secret.txt",
		"/clips/../secret.txt",
		"/clips/",
		"/clips/short.mp4",
		"/clips/not%20a%20key.mp4",
	} {
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, path, nil))
		if rec.Code != http.StatusNotFound {
			t.Errorf("%s: status = %d, want 404", path, rec.Code)
		}
	}
}

func TestClipDisabledWithoutDir(t *testing.T) {
	h := New(Config{}).Router()
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/clips/dQw4w9WgXcQ.mp4", nil))
	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want 404", rec.Code)
	}
}

func TestClipHead(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "dQw4w9WgXcQ.mp4"), []byte("mp4-bytes"), 0o644); err != nil {
		t.Fatal(err)
	}
	h := New(Config{ClipDir: dir}).Router()
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodHead, "/clips/dQw4w9WgXcQ.mp4", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d", rec.Code)
	}
	if rec.Body.Len() != 0 {
		t.Fatalf("HEAD leaked a body")
	}
}
