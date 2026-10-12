# JedFlix TV

Android TV / Google TV client for [JedFlix](https://github.com/JedBorseth/jedflix). 

Kotlin, Jetpack Compose for TV, Coil, Retrofit, Media3. Catalog from TMDB; playback through Real-Debrid.

![Home catalog](docs/home.png)![Stream picker](docs/streams.png)

**[Download APK](https://github.com/JedBorseth/jedflix-tv/releases/tag/v0.9.0)** · Leanback, API 24+ · sideload only (not on Play Store)


|          |                                                            |
| -------- | ---------------------------------------------------------- |
| Browse   | Home / Movies / Shows / Live TV, Trending billboard, deduplicated discovery, personal recommendations and 27 dynamic shelf themes |
| Search   | Title search as you type; personalized prompt buttons and custom discovery requests |
| Title    | Detail, cast, similar, TV episodes                         |
| Play     | Best-first English stream selection, Switch stream picker, TV chrome, captions, audio tracks, binge autoplay |
| Library  | Local profiles, My List, continue watching, search recents, Like and Not interested |
| Settings | Real-Debrid key on-device (type or QR from phone); Home shelf order and visibility; browse quality; version and in-app APK updates |


The RD key stays in DataStore on the TV. It is sent only to [Comet](https://comet.elfhosted.com) to find/unrestrict streams — never to JedFlix web.

Home recommendations use OpenRouter's free Nemotron 3 Super model over a broader public TMDB catalog. A persistent daily plan covers the [27 dynamic Shelf families](docs/dynamic-home.md), For You and Because you watched, with at most two Home model attempts per day across profiles. Search prompt buttons and **Find picks for my search** make fresh server requests; typing still searches TMDB by title. The TV retains profiles and viewing signals locally; the server stores public metadata and selected Title keys under anonymous cache tokens. Provider credentials stay on the server. See [cache, quotas and deployment](server/README.md#personalized-discovery).

Home hides unreleased movies and holds recent theatrical-only movies for 30 days, allowing earlier home releases, Canada-first streaming/rental/purchase availability, or recent successful playback. Metadata availability is an estimate; stream selection still happens on Play.

For older TVs, select **Settings → Browse quality → Low**. It uses smaller images, disables trailer previews, and keeps a clear focus border without poster scaling or glow. Viewing-time recording uses existing player save checkpoints and adds no timer or poster-focus writes.

## Todo

- More settings
- Alternate debrid providers (resell TorBox in-app)
- Actor pages
- More

## Install

1. Get a [Real-Debrid](https://real-debrid.com) premium key.
2. Install the APK (`adb install jedflix-tv-0.9.0.apk`, or copy onto the TV).
3. Settings → paste the key, or **Enter from phone** and scan the QR.

Later releases are offered in Settings (**Check for updates**, then **Download and install**). The app also checks GitHub once every 24 hours. Sideloaded updates only succeed when the new APK is signed with the same key as the installed build.



## Build

```properties
# local.properties (gitignored)
sdk.dir=/Users/you/Library/Android/sdk
TMDB_API_KEY=your_tmdb_v3_key
TRAILER_CLIP_BASE_URL=https://borseth.ddns.net/tv-api/clips
```

```bash
./gradlew :app:installDebug
```

Maestro (with e.g. `GoogleTV_1080p` running): `maestro test .maestro/home.yaml`
