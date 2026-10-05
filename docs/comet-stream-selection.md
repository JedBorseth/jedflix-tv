# Comet stream selection

Investigated 2026-10-03 against the [public ElfHosted configure page](https://comet.elfhosted.com/configure), [Comet commit f56677f](https://github.com/g0ldyy/comet/tree/f56677f05ed9a158f9a0338ad2f44d8efde35969), and the parser/ranker revision [65c24c7 pinned by that Comet commit](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/uv.lock#L1569). The public deployment does not advertise its commit; source conclusions describe the pinned upstream implementation, with its exposed configuration fields also present on the hosted page.

## Recommended configuration

| Field | Value | Reason |
| --- | --- | --- |
| `maxResultsPerResolution` | `0` | Unlimited results in each resolution bucket. |
| `maxSize` | `0` | No file-size cap. |
| `languages.required` | `["en"]` | Require recognized English metadata. |
| `options.remove_unknown_languages` | `true` | Explicitly reject unknown language metadata. |
| `options.allow_english_in_languages` | `false` | No language-exclusion bypass needed. |
| `options.remove_ranks_under` | `-10000000000` | Retain Comet's default threshold; avoid arbitrarily cutting off valid fallback streams. |
| `cachedOnly` | `true` | Return streams already available through debrid. |
| `enableTorrent` | `false` | Keep playback on debrid rather than peer-to-peer torrents. |
| `removeTrash` | `true` | Apply fetchability and language filters, including the default CAM/screener/telecine/telesync restrictions. |
| `deduplicateStreams` | `true` | Avoid repeating the same cached torrent across debrid services. |
| `sortCachedUncachedTogether` | `false` | Preserve cached-first ordering if uncached results are ever enabled. |

The [Comet configuration model](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/comet/core/models.py#L1268), [per-resolution selector](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/comet/api/endpoints/stream.py#L129), [ranking worker](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/comet/services/ranking.py), and [stream response assembly](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/comet/api/endpoints/stream.py#L436) establish these semantics. Deduplication adds little with a single Real-Debrid service but remains appropriate.

Language and options belong at the **top level** of the encoded configuration. Comet [constructs `rtnSettings` from its defaults plus top-level `resolutions`, `languages`, and `options`](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/comet/core/config_validation.py#L65).

## English is inferred from filenames

The parser [defaults languages to an empty list](https://github.com/g0ldyy/torrent-parse-rank/blob/65c24c70e93509a086481959f4ebce5a68a38196/crates/ptt-core/src/lib.rs#L366). Untagged releases are not assumed to be English. The [language filter](https://github.com/g0ldyy/torrent-parse-rank/blob/65c24c70e93509a086481959f4ebce5a68a38196/crates/rtn-core/src/lib.rs#L705) rejects empty languages when `required` is nonempty, even without `remove_unknown_languages`. Multilingual releases containing `en` pass; releases with only `multi` do not establish English. This strict requirement can remove otherwise playable English releases whose filenames omit their language.

These labels are not verified audio tracks. The [actual parser patterns](https://github.com/g0ldyy/torrent-parse-rank/blob/65c24c70e93509a086481959f4ebce5a68a38196/crates/ptt-core/src/generated/handlers.json#L3429) also recognize English-subtitle labels such as `English Subs` and `ESub` as `en`. Keep the player's English audio preference; Comet's language requirement alone cannot guarantee an English audio track.

## How best-first ordering works

Comet's locked [sort implementation](https://github.com/g0ldyy/torrent-parse-rank/blob/65c24c70e93509a086481959f4ebce5a68a38196/python/RTN/extras.py#L94) orders by resolution descending, then rank descending within that resolution, then infohash for ties. It does not sort by file size or seed count. Its [default rank model](https://github.com/g0ldyy/torrent-parse-rank/blob/65c24c70e93509a086481959f4ebce5a68a38196/python/RTN/models.py#L358) strongly favors remux, Dolby Vision, HDR, TrueHD, and Atmos. Resolution takes precedence even when a lower-resolution release has a higher rank.

Preserve the returned order for initial Play and fallback attempts. Max, Medium, and Low quality profiles should have no role in stream eligibility or selection; their browse-image and trailer behavior can remain separate.

## Limits of additional hosted filters

Current upstream [replaces caller-provided `rtnSettings` and `rtnRanking`](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/comet/core/config_validation.py#L77). Therefore the existing `rtnSettings.custom_ranks.audio.dts_lossless.fetch=false` must not be treated as an effective server guarantee. Retain it for deployments that honor it and preserve the local DTS-HD MA/DTS:X rejection.

The same limitation applies to proposed custom 3D, hardcoded-subtitle, codec, or HDR exclusions. Comet [explicitly enables fetching 3D and Dolby Vision by default](https://github.com/g0ldyy/comet/blob/f56677f05ed9a158f9a0338ad2f44d8efde35969/comet/core/models.py#L807); 3D is penalized by ranking but can still appear. HDR and codec names alone do not establish compatibility with a particular TV. Add capability-based local selection only after verifying device support, rather than discarding HDR or modern codecs globally. Supported cached-only, trash, English, and unlimited-result settings provide the concrete improvement without inventing ineffective hosted custom ranks.

## Playback verification finding

Real playback of the top English Matrix remux exposed an existing stereo-sink failure: FFmpeg decoded an 8-channel English track, but the app only registered mixing matrices through 6 channels. Media3 reported `No mixing matrix set for input channel count=8`, causing stream fallbacks. [Media3's default matrix factory](https://github.com/androidx/media/blob/1.11.0/libraries/common/src/main/java/androidx/media3/common/audio/ChannelMixingMatrix.java) only supplies layouts through 6 channels; [the processor](https://github.com/androidx/media/blob/1.11.0/libraries/common/src/main/java/androidx/media3/common/audio/ChannelMixingAudioProcessor.java) requires an explicit matrix for each input channel count.

JedFlix now registers 6.1 and 7.1 stereo matrices in addition to the existing layouts, following Android PCM channel order and [Android's stereo downmix coefficients](https://android.googlesource.com/platform/system/media/+/refs/heads/main/audio_utils/include/audio_utils/ChannelMix.h). This lets high-ranked TrueHD/Atmos releases play on stereo TVs without being rejected solely for an absent downmix layout. DTS-lossless release rejection remains separate.

The same release's Dolby Vision profile exceeded the emulator's video decoder capabilities. Media3 selected only its audio and advanced the timeline with a black picture, without reporting a player error. JedFlix now treats discovered video groups with no supported track as a stream failure and tries the next candidate. Empty track groups during preparation do not trigger fallback. This is checked against actual device support, rather than a global HDR exclusion. The before/after verification uses real playback on the emulator; constructing Media3 track groups in the plain JVM test suite requires Android framework methods, so no stub-based track test was added.

Verified 2026-10-04: all 205 JVM tests pass; the signed 0.6.5 APK upgrades in place with the API key and Low browse setting preserved. Real Matrix Play bypasses the picker, advances past unsupported formats, renders a 1920×800 picture, and selects English Dolby Digital 5.1. Switch stream shows more than five 2160p rows. The broader debug lint check reports existing API-level and Media3 opt-in issues.
