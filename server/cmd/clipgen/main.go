// clipgen warms the 15s trailer clips the TV catalog preview plays.
//
// It runs as a one-shot job (cron: `docker compose run --rm clipgen`), walks
// the configured TMDB lists one Title at a time, and writes {youtubeKey}.mp4
// into CLIP_DIR. Everything it does is logged to stdout and to
// CLIP_DIR/logs/clipgen-<timestamp>.log so a failed yt-dlp run is readable
// after the container is gone.
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/JedBorseth/jedflix-tv/server/internal/clips"
)

// Version is stamped by the Docker build (-ldflags "-X main.Version=<sha>").
var Version = "dev"

func main() {
	dir := flag.String("dir", envOr("CLIP_DIR", "/clips"), "directory that holds {youtubeKey}.mp4 (CLIP_DIR)")
	lists := flag.String("lists", os.Getenv("CLIP_LISTS"), "optional TMDB lists instead of the Home catalog, e.g. movie:123,tv:456 (CLIP_LISTS)")
	limit := flag.Int("limit", 0, "stop after this many generation attempts (0 = all)")
	key := flag.String("key", "", "generate a single YouTube key and exit (skips TMDB)")
	maxBytes := flag.Int64("max-bytes", 5<<20, "hard cap per clip")
	perTitle := flag.Duration("per-title-timeout", 10*time.Minute, "deadline for one download+encode")
	cookies := flag.String("cookies", os.Getenv("YTDLP_COOKIES"), "optional Netscape cookies file for yt-dlp (YTDLP_COOKIES)")
	noLogFile := flag.Bool("no-log-file", false, "log to stdout only")
	flag.Parse()

	logw, closeLog, err := openLog(*dir, *noLogFile)
	if err != nil {
		log.Fatalf("log: %v", err)
	}
	defer closeLog()
	logger := log.New(logw, "", log.LstdFlags)
	logger.Printf("clipgen %s dir=%s max-bytes=%d limit=%d", Version, *dir, *maxBytes, *limit)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	tools := clips.Tools{
		YtDlp:    envOr("YTDLP_BIN", "yt-dlp"),
		FFmpeg:   envOr("FFMPEG_BIN", "ffmpeg"),
		FFprobe:  envOr("FFPROBE_BIN", "ffprobe"),
		MaxBytes: *maxBytes,
		Log:      logw,
		Cookies:  *cookies,
	}

	if *key != "" {
		if err := os.MkdirAll(*dir, 0o775); err != nil {
			logger.Fatalf("clip dir: %v", err)
		}
		tctx, cancel := context.WithTimeout(ctx, *perTitle)
		defer cancel()
		dst := filepath.Join(*dir, clips.FileName(*key))
		logger.Printf("START key=%s", *key)
		res, err := tools.Generate(tctx, *key, dst)
		if err != nil {
			logger.Printf("FAIL  key=%s err=%v", *key, err)
			os.Exit(1)
		}
		logger.Printf("OK    key=%s bytes=%d duration=%.1fs profile=%s window=%.1f-%.1f",
			*key, res.Bytes, res.Duration, res.Profile, res.Window.Start, res.Window.End())
		return
	}

	apiKey := os.Getenv("TMDB_API_KEY")
	if apiKey == "" {
		logger.Fatal("TMDB_API_KEY is not set")
	}

	var shelves []clips.Shelf
	if *lists != "" {
		specs, err := clips.ParseListSpecs(*lists)
		if err != nil {
			logger.Fatalf("lists: %v", err)
		}
		for _, spec := range specs {
			shelves = append(shelves, clips.Shelf{
				Name:      fmt.Sprintf("list %d", spec.ID),
				Kind:      clips.ShelfEditorial,
				MediaType: spec.MediaType,
				ListID:    spec.ID,
			})
		}
	} else {
		shelves = clips.HomeShelves()
	}

	job := &clips.Job{
		Fetch:     &clips.TMDB{APIKey: apiKey, BaseURL: os.Getenv("TMDB_BASE_URL")},
		Gen:       tools,
		Dir:       *dir,
		Shelves:   shelves,
		Limit:     *limit,
		MaxBytes:  *maxBytes,
		PerTitle:  *perTitle,
		Log:       logger,
		KeepGoing: true,
	}
	sum, err := job.Run(ctx)
	if err != nil {
		logger.Printf("ABORT %s err=%v", sum, err)
		os.Exit(1)
	}
	if sum.Fail > 0 {
		os.Exit(2)
	}
}

func envOr(name, def string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return def
}

// openLog tees stdout with CLIP_DIR/logs/clipgen-<ts>.log.
func openLog(dir string, stdoutOnly bool) (io.Writer, func(), error) {
	if stdoutOnly {
		return os.Stdout, func() {}, nil
	}
	logDir := filepath.Join(dir, "logs")
	if err := os.MkdirAll(logDir, 0o775); err != nil {
		return nil, nil, err
	}
	name := filepath.Join(logDir, fmt.Sprintf("clipgen-%s.log", time.Now().Format("20060102-150405")))
	f, err := os.OpenFile(name, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o664)
	if err != nil {
		return nil, nil, err
	}
	fmt.Fprintf(os.Stdout, "logging to %s\n", name)
	return io.MultiWriter(os.Stdout, f), func() { _ = f.Close() }, nil
}
