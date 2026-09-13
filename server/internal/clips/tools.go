package clips

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// EncodeProfile is one ffmpeg attempt. Profiles step down until the clip fits
// the byte cap.
type EncodeProfile struct {
	Name        string
	MaxWidth    int
	CRF         int
	MaxRateKbps int
	AudioKbps   int
}

// Profiles is the ladder tried in order. 15s at ~2.4Mbps is ~4.7MB, so 720p
// normally fits 5MB; 480p is the fallback for busy trailers.
var Profiles = []EncodeProfile{
	{Name: "720p", MaxWidth: 1280, CRF: 23, MaxRateKbps: 2400, AudioKbps: 96},
	{Name: "480p", MaxWidth: 854, CRF: 26, MaxRateKbps: 1500, AudioKbps: 64},
}

// Result describes a published clip.
type Result struct {
	Bytes    int64
	Duration float64
	Profile  string
	Window   Window
}

// Generator produces `{key}.mp4` at dst. The job depends on this interface so
// tests can run without yt-dlp.
type Generator interface {
	Generate(ctx context.Context, key, dst string) (Result, error)
}

// Tools shells out to yt-dlp and ffmpeg. Every command line and all of its
// stderr go to Log so a YouTube bot-check or a format failure is readable in
// the run log.
type Tools struct {
	YtDlp    string
	FFmpeg   string
	FFprobe  string
	MaxBytes int64
	Log      io.Writer
	// Cookies is an optional Netscape cookies file passed to yt-dlp.
	Cookies string
}

func (t Tools) logf(format string, args ...any) {
	if t.Log == nil {
		return
	}
	fmt.Fprintf(t.Log, format+"\n", args...)
}

// run executes a command, streaming combined output into the log line by
// line with a prefix. It returns the trimmed stdout for callers that need it.
func (t Tools) run(ctx context.Context, prefix string, name string, args ...string) (string, error) {
	t.logf("  $ %s %s", name, strings.Join(args, " "))
	cmd := exec.CommandContext(ctx, name, args...)
	var stdout strings.Builder
	pr, pw := io.Pipe()
	cmd.Stdout = io.MultiWriter(&stdout, pw)
	cmd.Stderr = pw
	done := make(chan struct{})
	go func() {
		defer close(done)
		sc := bufio.NewScanner(pr)
		sc.Buffer(make([]byte, 64*1024), 1024*1024)
		for sc.Scan() {
			line := strings.TrimSpace(sc.Text())
			if line != "" {
				t.logf("  [%s] %s", prefix, line)
			}
		}
	}()
	err := cmd.Run()
	pw.Close()
	<-done
	if err != nil {
		return strings.TrimSpace(stdout.String()), fmt.Errorf("%s: %w", name, err)
	}
	return strings.TrimSpace(stdout.String()), nil
}

func (t Tools) ytArgs(extra ...string) []string {
	args := []string{"--no-playlist", "--no-cache-dir", "--no-progress", "--newline", "--retries", "3", "--js-runtimes", "node"}
	if t.Cookies != "" {
		args = append(args, "--cookies", t.Cookies)
	}
	return append(args, extra...)
}

// Duration asks yt-dlp how long the source video is, in seconds.
func (t Tools) Duration(ctx context.Context, key string) (float64, error) {
	out, err := t.run(ctx, "yt-dlp", t.YtDlp, t.ytArgs(
		"--skip-download", "--print", "%(duration)s", youtubeURL(key),
	)...)
	if err != nil {
		return 0, err
	}
	// yt-dlp prints one line per video; take the last non-empty one.
	lines := strings.Fields(out)
	if len(lines) == 0 {
		return 0, errors.New("yt-dlp printed no duration")
	}
	d, err := strconv.ParseFloat(lines[len(lines)-1], 64)
	if err != nil || d <= 0 {
		return 0, fmt.Errorf("bad duration %q", lines[len(lines)-1])
	}
	return d, nil
}

const ytdlpFormat = "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b"

// DownloadSection has yt-dlp fetch only the window at up to 720p into out.
func (t Tools) DownloadSection(ctx context.Context, key string, w Window, out string) error {
	_, err := t.run(ctx, "yt-dlp", t.YtDlp, t.ytArgs(
		"-f", ytdlpFormat,
		"--download-sections", w.SectionSpec(),
		"--force-keyframes-at-cuts",
		"--merge-output-format", "mp4",
		"-o", out,
		youtubeURL(key),
	)...)
	return err
}

// DownloadFull fetches the whole trailer when --download-sections fails.
func (t Tools) DownloadFull(ctx context.Context, key, out string) error {
	_, err := t.run(ctx, "yt-dlp", t.YtDlp, t.ytArgs(
		"-f", ytdlpFormat,
		"--merge-output-format", "mp4",
		"-o", out,
		youtubeURL(key),
	)...)
	return err
}

// Encode re-encodes in to a progressive H.264/AAC MP4 with faststart.
// When seek is true, ffmpeg cuts the window from a full-length download.
func (t Tools) Encode(ctx context.Context, in, out string, w Window, p EncodeProfile, seek bool) error {
	scale := fmt.Sprintf("scale='min(%d,iw)':-2", p.MaxWidth)
	args := []string{"-hide_banner", "-loglevel", "warning", "-nostdin", "-y"}
	if seek {
		args = append(args, "-ss", fmt.Sprintf("%.2f", w.Start))
	}
	args = append(args,
		"-i", in,
		"-t", fmt.Sprintf("%.2f", w.Length),
		"-vf", scale,
		"-c:v", "libx264", "-preset", "medium", "-profile:v", "high", "-pix_fmt", "yuv420p",
		"-crf", strconv.Itoa(p.CRF),
		"-maxrate", fmt.Sprintf("%dk", p.MaxRateKbps),
		"-bufsize", fmt.Sprintf("%dk", p.MaxRateKbps*2),
		"-c:a", "aac", "-b:a", fmt.Sprintf("%dk", p.AudioKbps), "-ac", "2",
		"-movflags", "+faststart",
		"-map_metadata", "-1",
		"-sn", "-dn",
		out,
	)
	_, err := t.run(ctx, "ffmpeg", t.FFmpeg, args...)
	return err
}

// ProbeDuration reads the container duration of a finished file.
func (t Tools) ProbeDuration(ctx context.Context, path string) (float64, error) {
	out, err := t.run(ctx, "ffprobe", t.FFprobe,
		"-v", "error", "-show_entries", "format=duration",
		"-of", "default=noprint_wrappers=1:nokey=1", path,
	)
	if err != nil {
		return 0, err
	}
	return strconv.ParseFloat(strings.TrimSpace(out), 64)
}

// Generate runs the whole ladder for one key and atomically publishes dst.
func (t Tools) Generate(ctx context.Context, key, dst string) (Result, error) {
	if !ValidKey(key) {
		return Result{}, fmt.Errorf("invalid youtube key %q", key)
	}
	dir := filepath.Dir(dst)
	tmpRoot := filepath.Join(dir, ".tmp")
	if err := os.MkdirAll(tmpRoot, 0o775); err != nil {
		return Result{}, err
	}
	work, err := os.MkdirTemp(tmpRoot, key+"-")
	if err != nil {
		return Result{}, err
	}
	defer os.RemoveAll(work)

	duration, err := t.Duration(ctx, key)
	if err != nil {
		return Result{}, fmt.Errorf("duration: %w", err)
	}
	w, err := ClipWindow(duration)
	if err != nil {
		return Result{}, err
	}
	t.logf("  source=%.1fs window=%.1fs..%.1fs", duration, w.Start, w.End())

	raw := filepath.Join(work, "raw.mp4")
	seek := false
	if err := t.DownloadSection(ctx, key, w, raw); err != nil {
		t.logf("  section download failed (%v); falling back to full download", err)
		if err := t.DownloadFull(ctx, key, raw); err != nil {
			return Result{}, fmt.Errorf("download: %w", err)
		}
		seek = true
	}
	if st, err := os.Stat(raw); err != nil || st.Size() == 0 {
		return Result{}, errors.New("download produced no file")
	}

	for _, p := range Profiles {
		out := filepath.Join(work, p.Name+".mp4")
		start := time.Now()
		if err := t.Encode(ctx, raw, out, w, p, seek); err != nil {
			return Result{}, fmt.Errorf("encode %s: %w", p.Name, err)
		}
		st, err := os.Stat(out)
		if err != nil {
			return Result{}, err
		}
		t.logf("  encoded %s bytes=%d in %s", p.Name, st.Size(), time.Since(start).Round(time.Millisecond))
		if t.MaxBytes > 0 && st.Size() > t.MaxBytes {
			t.logf("  %s over cap (%d > %d), stepping down", p.Name, st.Size(), t.MaxBytes)
			continue
		}
		got, err := t.ProbeDuration(ctx, out)
		if err != nil {
			return Result{}, fmt.Errorf("probe: %w", err)
		}
		if err := os.Chmod(out, 0o664); err != nil {
			return Result{}, err
		}
		if err := os.Rename(out, dst); err != nil {
			return Result{}, fmt.Errorf("publish: %w", err)
		}
		return Result{Bytes: st.Size(), Duration: got, Profile: p.Name, Window: w}, nil
	}
	return Result{}, fmt.Errorf("every profile exceeded %d bytes", t.MaxBytes)
}

func youtubeURL(key string) string { return "https://www.youtube.com/watch?v=" + key }
