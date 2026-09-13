// Package clips turns TMDB YouTube videos into the 15s progressive MP4s the TV
// catalog preview plays. It is a serial batch job: one Title at a time, every
// step logged, a failure moves on to the next Title.
package clips

import "strings"

// Video is one entry from TMDB `/{movie|tv}/{id}/videos`.
type Video struct {
	Key      string `json:"key"`
	Site     string `json:"site"`
	Type     string `json:"type"`
	Official bool   `json:"official"`
	ISO6391  string `json:"iso_639_1"`
}

// PickYouTubeKey mirrors the Android TrailerPicker so the server generates the
// same clip the client asks for: official EN trailer, EN trailer, any trailer,
// the same ladder for teasers, then any remaining YouTube video (Clip,
// Featurette, …). Empty when TMDB has no YouTube video.
func PickYouTubeKey(videos []Video) string {
	var youtube []Video
	for _, v := range videos {
		if strings.EqualFold(v.Site, "YouTube") && strings.TrimSpace(v.Key) != "" {
			youtube = append(youtube, v)
		}
	}
	if len(youtube) == 0 {
		return ""
	}
	isType := func(v Video, t string) bool { return strings.EqualFold(v.Type, t) }
	isEN := func(v Video) bool { return strings.EqualFold(v.ISO6391, "en") }

	ladder := []func(Video) bool{
		func(v Video) bool { return isType(v, "Trailer") && v.Official && isEN(v) },
		func(v Video) bool { return isType(v, "Trailer") && isEN(v) },
		func(v Video) bool { return isType(v, "Trailer") },
		func(v Video) bool { return isType(v, "Teaser") && v.Official && isEN(v) },
		func(v Video) bool { return isType(v, "Teaser") && isEN(v) },
		func(v Video) bool { return isType(v, "Teaser") },
		func(v Video) bool { return v.Official && isEN(v) },
		func(v Video) bool { return isEN(v) },
	}
	for _, match := range ladder {
		for _, v := range youtube {
			if match(v) {
				return v.Key
			}
		}
	}
	return youtube[0].Key
}
