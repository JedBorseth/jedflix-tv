package clips

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
)

// ClipSeconds is how much of the trailer the catalog preview plays.
const ClipSeconds = 15.0

// Window is the slice of the source trailer to keep, in seconds.
type Window struct {
	Start  float64
	Length float64
}

// End is the exclusive end offset in the source.
func (w Window) End() float64 { return w.Start + w.Length }

// ClipWindow picks 15s starting a third of the way into a trailer, so the clip
// lands past the studio bumpers and into the movie. Shorter sources keep
// everything; a start that would overrun takes the final 15s instead.
func ClipWindow(duration float64) (Window, error) {
	if duration <= 0 {
		return Window{}, errors.New("unknown source duration")
	}
	if duration <= ClipSeconds {
		return Window{Start: 0, Length: duration}, nil
	}
	start := duration / 3
	if start+ClipSeconds > duration {
		start = duration - ClipSeconds
	}
	return Window{Start: start, Length: ClipSeconds}, nil
}

// SectionSpec formats the window the way yt-dlp --download-sections expects.
func (w Window) SectionSpec() string {
	return fmt.Sprintf("*%.2f-%.2f", w.Start, w.End())
}

var youtubeKey = regexp.MustCompile(`^[A-Za-z0-9_-]{6,20}$`)

// ValidKey reports whether s is shaped like a YouTube video id. Both clipgen
// and the HTTP host use it so a bad TMDB key can never become a path.
func ValidKey(s string) bool { return youtubeKey.MatchString(s) }

// FileName is the on-disk (and URL) name for a key's clip.
func FileName(key string) string { return key + ".mp4" }

// KeyFromFile extracts a YouTube key from a `{key}.mp4` basename. False when
// the name is not a clip we would ever publish (including path tricks).
func KeyFromFile(name string) (string, bool) {
	if strings.ContainsAny(name, `/\`) {
		return "", false
	}
	key, ok := strings.CutSuffix(name, ".mp4")
	if !ok || !ValidKey(key) {
		return "", false
	}
	return key, true
}
