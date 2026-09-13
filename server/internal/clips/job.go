package clips

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"time"
)

// Job warms clips for a set of TMDB shelves, strictly one Title at a time.
type Job struct {
	Fetch     Fetcher
	Gen       Generator
	Dir       string
	Shelves   []Shelf
	Lists     []ListSpec // convenience: editorial lists, used when Shelves is empty
	Limit     int        // 0 = no limit; >0 stops after that many generation attempts
	MaxBytes  int64      // an existing file over this is regenerated
	PerTitle  time.Duration
	Log       *log.Logger
	KeepGoing bool // continue past per-title failures (always true in cron)
}

// Summary is the end-of-run tally.
type Summary struct {
	Titles    int
	OK        int
	Skip      int
	Fail      int
	NoTrailer int
}

func (s Summary) String() string {
	return fmt.Sprintf("titles=%d ok=%d skip=%d fail=%d no-trailer=%d", s.Titles, s.OK, s.Skip, s.Fail, s.NoTrailer)
}

// Run walks every list in order. It returns an error only when the run could
// not proceed at all (bad dir, list fetch failed); per-Title failures are
// counted and logged.
func (j *Job) Run(ctx context.Context) (Summary, error) {
	var sum Summary
	if j.Log == nil {
		j.Log = log.Default()
	}
	if err := os.MkdirAll(j.Dir, 0o775); err != nil {
		return sum, fmt.Errorf("clip dir: %w", err)
	}
	perTitle := j.PerTitle
	if perTitle <= 0 {
		perTitle = 10 * time.Minute
	}

	var titles []Title
	seen := map[string]bool{}
	shelves := j.Shelves
	if len(shelves) == 0 {
		for _, list := range j.Lists {
			shelves = append(shelves, Shelf{
				Name:      fmt.Sprintf("list %d", list.ID),
				Kind:      ShelfEditorial,
				MediaType: list.MediaType,
				ListID:    list.ID,
			})
		}
	}
	for _, shelf := range shelves {
		got, err := j.Fetch.Titles(ctx, shelf)
		if err != nil {
			return sum, fmt.Errorf("shelf %s: %w", shelf.Name, err)
		}
		j.Log.Printf("SHELF name=%q kind=%s titles=%d", shelf.Name, shelf.Kind, len(got))
		for _, t := range got {
			key := t.MediaType + ":" + fmt.Sprintf("%d", t.ID)
			if seen[key] {
				continue
			}
			seen[key] = true
			titles = append(titles, t)
		}
	}
	sum.Titles = len(titles)
	j.Log.Printf("QUEUE unique=%d", len(titles))

	attempts := 0
	for i, t := range titles {
		if ctx.Err() != nil {
			return sum, ctx.Err()
		}
		if j.Limit > 0 && attempts >= j.Limit {
			j.Log.Printf("LIMIT reached after %d generation attempt(s); %d title(s) left", attempts, len(titles)-i)
			break
		}
		j.Log.Printf("START [%d/%d] title=%q tmdb=%d type=%s", i+1, len(titles), t.Name, t.ID, t.MediaType)

		videos, err := j.Fetch.Videos(ctx, t.MediaType, t.ID)
		if err != nil {
			sum.Fail++
			j.Log.Printf("FAIL  title=%q tmdb=%d err=videos: %v", t.Name, t.ID, err)
			if !j.KeepGoing {
				return sum, err
			}
			continue
		}
		key := PickYouTubeKey(videos)
		if key == "" {
			sum.NoTrailer++
			j.Log.Printf("NONE  title=%q tmdb=%d videos=%d (no YouTube video)", t.Name, t.ID, len(videos))
			continue
		}
		if !ValidKey(key) {
			sum.Fail++
			j.Log.Printf("FAIL  title=%q tmdb=%d key=%q err=key does not look like a YouTube id", t.Name, t.ID, key)
			continue
		}

		dst := filepath.Join(j.Dir, FileName(key))
		if st, err := os.Stat(dst); err == nil && st.Size() > 0 && (j.MaxBytes <= 0 || st.Size() <= j.MaxBytes) {
			sum.Skip++
			j.Log.Printf("SKIP  title=%q key=%s exists bytes=%d", t.Name, key, st.Size())
			continue
		}

		attempts++
		tctx, cancel := context.WithTimeout(ctx, perTitle)
		started := time.Now()
		res, err := j.Gen.Generate(tctx, key, dst)
		cancel()
		if err != nil {
			sum.Fail++
			j.Log.Printf("FAIL  title=%q tmdb=%d key=%s after=%s err=%v", t.Name, t.ID, key, time.Since(started).Round(time.Millisecond), err)
			if !j.KeepGoing && !errors.Is(err, context.DeadlineExceeded) {
				return sum, err
			}
			continue
		}
		sum.OK++
		j.Log.Printf("OK    title=%q key=%s bytes=%d duration=%.1fs profile=%s window=%.1f-%.1f took=%s",
			t.Name, key, res.Bytes, res.Duration, res.Profile, res.Window.Start, res.Window.End(), time.Since(started).Round(time.Millisecond))
	}
	j.Log.Printf("DONE  %s", sum)
	return sum, nil
}
