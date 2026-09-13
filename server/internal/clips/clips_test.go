package clips

import (
	"bytes"
	"context"
	"errors"
	"log"
	"math"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestHomeShelvesCoverTheCatalog(t *testing.T) {
	got := HomeShelves()
	if len(got) < 15 {
		t.Fatalf("home shelves %d, want the full Home catalog", len(got))
	}
	var names []string
	for _, s := range got {
		names = append(names, s.Name)
	}
	joined := strings.Join(names, ",")
	for _, need := range []string{"Jed's Movies", "Jed's Shows", "Trending Now", "Crave Movies", "Disney+ Shows", "Action Movies"} {
		if !strings.Contains(joined, need) {
			t.Errorf("missing %q in %s", need, joined)
		}
	}
}

func TestPickYouTubeKeyPrefersOfficialEnglishTrailer(t *testing.T) {
	videos := []Video{
		{Key: "teaser", Site: "YouTube", Type: "Teaser", Official: true, ISO6391: "en"},
		{Key: "vimeo", Site: "Vimeo", Type: "Trailer", Official: true, ISO6391: "en"},
		{Key: "frTrailer", Site: "YouTube", Type: "Trailer", Official: true, ISO6391: "fr"},
		{Key: "fanTrailer", Site: "YouTube", Type: "Trailer", Official: false, ISO6391: "en"},
		{Key: "official", Site: "youtube", Type: "trailer", Official: true, ISO6391: "EN"},
	}
	if got := PickYouTubeKey(videos); got != "official" {
		t.Fatalf("got %q, want official", got)
	}
}

func TestPickYouTubeKeyLadder(t *testing.T) {
	cases := []struct {
		name   string
		videos []Video
		want   string
	}{
		{"english trailer over other trailer", []Video{
			{Key: "de", Site: "YouTube", Type: "Trailer", ISO6391: "de"},
			{Key: "en", Site: "YouTube", Type: "Trailer", ISO6391: "en"},
		}, "en"},
		{"any trailer over teaser", []Video{
			{Key: "teaser", Site: "YouTube", Type: "Teaser", Official: true, ISO6391: "en"},
			{Key: "de", Site: "YouTube", Type: "Trailer", ISO6391: "de"},
		}, "de"},
		{"teaser when no trailer", []Video{
			{Key: "clip", Site: "YouTube", Type: "Clip", ISO6391: "en"},
			{Key: "teaser", Site: "YouTube", Type: "Teaser", ISO6391: "en"},
		}, "teaser"},
		{"clip when no trailer or teaser", []Video{
			{Key: "clip", Site: "YouTube", Type: "Clip", Official: true, ISO6391: "en"},
		}, "clip"},
		{"nothing usable", []Video{
			{Key: "clip", Site: "YouTube", Type: "Clip"},
			{Key: "", Site: "YouTube", Type: "Trailer"},
		}, "clip"},
		{"empty", nil, ""},
	}
	for _, c := range cases {
		if got := PickYouTubeKey(c.videos); got != c.want {
			t.Errorf("%s: got %q want %q", c.name, got, c.want)
		}
	}
}

func TestClipWindow(t *testing.T) {
	cases := []struct {
		dur         float64
		wantStart   float64
		wantLength  float64
		wantErr     bool
		description string
	}{
		{0, 0, 0, true, "unknown duration fails"},
		{-3, 0, 0, true, "negative fails"},
		{10, 0, 10, false, "short source keeps everything"},
		{15, 0, 15, false, "exactly 15 keeps everything"},
		{20, 5, 15, false, "20s: start 6.67 would overrun, take last 15"},
		{90, 30, 15, false, "90s: start a third in"},
		{150, 50, 15, false, "150s: start a third in"},
	}
	for _, c := range cases {
		w, err := ClipWindow(c.dur)
		if c.wantErr {
			if err == nil {
				t.Errorf("%s: want error", c.description)
			}
			continue
		}
		if err != nil {
			t.Errorf("%s: %v", c.description, err)
			continue
		}
		if math.Abs(w.Start-c.wantStart) > 0.01 || math.Abs(w.Length-c.wantLength) > 0.01 {
			t.Errorf("%s: got %+v want start=%v length=%v", c.description, w, c.wantStart, c.wantLength)
		}
		if w.End() > c.dur+0.01 {
			t.Errorf("%s: window end %v past duration %v", c.description, w.End(), c.dur)
		}
	}
}

func TestSectionSpec(t *testing.T) {
	if got := (Window{Start: 30, Length: 15}).SectionSpec(); got != "*30.00-45.00" {
		t.Fatalf("got %q", got)
	}
}

func TestKeyFromFile(t *testing.T) {
	key, ok := KeyFromFile("dQw4w9WgXcQ.mp4")
	if !ok || key != "dQw4w9WgXcQ" {
		t.Fatalf("got %q %v", key, ok)
	}
	for _, name := range []string{"", "dQw4w9WgXcQ", "../secret.mp4", "a/b.mp4", "logs.mp4", "short.mp4"} {
		if _, ok := KeyFromFile(name); ok {
			t.Errorf("%q should be rejected", name)
		}
	}
}

func TestValidKey(t *testing.T) {
	good := []string{"dQw4w9WgXcQ", "a-b_c1", "ABCDEFGHIJKLMNOPQRST"}
	bad := []string{"", "short", "../etc/passwd", "has space", "toolongtoolongtoolongt", "key.mp4", "a/b"}
	for _, k := range good {
		if !ValidKey(k) {
			t.Errorf("%q should be valid", k)
		}
	}
	for _, k := range bad {
		if ValidKey(k) {
			t.Errorf("%q should be invalid", k)
		}
	}
}

func TestParseListSpecs(t *testing.T) {
	specs, err := ParseListSpecs("movie:8693449, tv:8693452")
	if err != nil {
		t.Fatal(err)
	}
	if len(specs) != 2 || specs[0] != (ListSpec{8693449, "movie"}) || specs[1] != (ListSpec{8693452, "tv"}) {
		t.Fatalf("got %+v", specs)
	}
	for _, bad := range []string{"", "8693449", "show:1", "movie:x", "movie:0"} {
		if _, err := ParseListSpecs(bad); err == nil {
			t.Errorf("%q should fail", bad)
		}
	}
}

type fakeFetch struct {
	titles map[int][]Title
	videos map[int][]Video
	err    map[int]error
}

func (f fakeFetch) Titles(_ context.Context, s Shelf) ([]Title, error) {
	return f.titles[s.ListID], nil
}

func (f fakeFetch) Videos(_ context.Context, _ string, id int) ([]Video, error) {
	if err := f.err[id]; err != nil {
		return nil, err
	}
	return f.videos[id], nil
}

type fakeGen struct {
	calls []string
	fail  map[string]error
	bytes int64
}

func (g *fakeGen) Generate(_ context.Context, key, dst string) (Result, error) {
	g.calls = append(g.calls, key)
	if err := g.fail[key]; err != nil {
		return Result{}, err
	}
	if err := os.WriteFile(dst, bytes.Repeat([]byte{0}, int(g.bytes)), 0o644); err != nil {
		return Result{}, err
	}
	return Result{Bytes: g.bytes, Duration: 15, Profile: "720p", Window: Window{Start: 30, Length: 15}}, nil
}

func TestJobRunsSeriallyAndContinuesPastFailures(t *testing.T) {
	dir := t.TempDir()
	// Pre-existing good clip for title 3 should be skipped.
	if err := os.WriteFile(filepath.Join(dir, "existing001.mp4"), []byte("mp4"), 0o644); err != nil {
		t.Fatal(err)
	}
	// Oversized existing clip for title 5 should be regenerated.
	if err := os.WriteFile(filepath.Join(dir, "toobig00001.mp4"), bytes.Repeat([]byte{1}, 20), 0o644); err != nil {
		t.Fatal(err)
	}

	fetch := fakeFetch{
		titles: map[int][]Title{
			1: {{ID: 1, MediaType: "movie", Name: "Good"}, {ID: 2, MediaType: "movie", Name: "Broken"}, {ID: 3, MediaType: "movie", Name: "Cached"}},
			2: {{ID: 4, MediaType: "tv", Name: "No trailer"}, {ID: 5, MediaType: "tv", Name: "Too big"}, {ID: 6, MediaType: "tv", Name: "Videos 500"}},
		},
		videos: map[int][]Video{
			1: {{Key: "goodkey0001", Site: "YouTube", Type: "Trailer", ISO6391: "en"}},
			2: {{Key: "brokenkey01", Site: "YouTube", Type: "Trailer", ISO6391: "en"}},
			3: {{Key: "existing001", Site: "YouTube", Type: "Trailer", ISO6391: "en"}},
			4: {{Key: "vimeo", Site: "Vimeo", Type: "Trailer"}},
			5: {{Key: "toobig00001", Site: "YouTube", Type: "Teaser"}},
		},
		err: map[int]error{6: errors.New("tmdb 500")},
	}
	gen := &fakeGen{fail: map[string]error{"brokenkey01": errors.New("yt-dlp: Sign in to confirm you're not a bot")}, bytes: 10}
	var out bytes.Buffer
	job := &Job{
		Fetch:     fetch,
		Gen:       gen,
		Dir:       dir,
		Lists:     []ListSpec{{1, "movie"}, {2, "tv"}},
		MaxBytes:  15,
		Log:       log.New(&out, "", 0),
		KeepGoing: true,
	}
	sum, err := job.Run(context.Background())
	if err != nil {
		t.Fatalf("run: %v", err)
	}
	want := Summary{Titles: 6, OK: 2, Skip: 1, Fail: 2, NoTrailer: 1}
	if sum != want {
		t.Fatalf("summary %+v, want %+v\n%s", sum, want, out.String())
	}
	if strings.Join(gen.calls, ",") != "goodkey0001,brokenkey01,toobig00001" {
		t.Fatalf("generate order %v", gen.calls)
	}
	logText := out.String()
	for _, needle := range []string{
		`SHELF name="list 1"`,
		`QUEUE unique=6`,
		`START [1/6] title="Good"`,
		`OK    title="Good" key=goodkey0001`,
		`FAIL  title="Broken" tmdb=2 key=brokenkey01`,
		"not a bot",
		`SKIP  title="Cached" key=existing001 exists bytes=3`,
		`NONE  title="No trailer"`,
		`OK    title="Too big" key=toobig00001`,
		`FAIL  title="Videos 500" tmdb=6 err=videos: tmdb 500`,
		"DONE  titles=6 ok=2 skip=1 fail=2 no-trailer=1",
	} {
		if !strings.Contains(logText, needle) {
			t.Errorf("log missing %q\n%s", needle, logText)
		}
	}
	if st, err := os.Stat(filepath.Join(dir, "toobig00001.mp4")); err != nil || st.Size() != 10 {
		t.Fatalf("oversized clip not regenerated: %v %v", st, err)
	}
}

func TestJobLimitCountsOnlyGenerationAttempts(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "cached00001.mp4"), []byte("mp4"), 0o644); err != nil {
		t.Fatal(err)
	}
	fetch := fakeFetch{
		titles: map[int][]Title{1: {{ID: 1, Name: "A", MediaType: "movie"}, {ID: 2, Name: "B", MediaType: "movie"}, {ID: 3, Name: "C", MediaType: "movie"}}},
		videos: map[int][]Video{
			1: {{Key: "cached00001", Site: "YouTube", Type: "Trailer"}},
			2: {{Key: "second00001", Site: "YouTube", Type: "Trailer"}},
			3: {{Key: "third000001", Site: "YouTube", Type: "Trailer"}},
		},
	}
	gen := &fakeGen{bytes: 1}
	job := &Job{Fetch: fetch, Gen: gen, Dir: dir, Lists: []ListSpec{{1, "movie"}}, Limit: 1, Log: log.New(&bytes.Buffer{}, "", 0), KeepGoing: true}
	sum, err := job.Run(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(gen.calls) != 1 || gen.calls[0] != "second00001" {
		t.Fatalf("calls %v", gen.calls)
	}
	if sum.Skip != 1 || sum.OK != 1 {
		t.Fatalf("summary %+v", sum)
	}
}
