# JedFlix TV

Android TV client for [JedFlix](https://github.com/JedBorseth/jedflix). Kotlin, Jetpack Compose for TV
(`androidx.tv:tv-material`), Coil, Retrofit + kotlinx.serialization, Maestro.

Phase 1 is browse only: a Netflix-style home with a launch animation, nav rail (Home / Movies / Shows),
immersive billboard and TMDB poster rows. Playback, details, auth and My List come later and will talk to
the existing JedFlix Go backend.

## Setup

1. Copy `local.properties.example` to `local.properties`, set `sdk.dir` and your TMDB v3 key:

   ```properties
   sdk.dir=/Users/you/Library/Android/sdk
   TMDB_API_KEY=your_key_here
   ```

   `local.properties` is gitignored; the key is injected as `BuildConfig.TMDB_API_KEY`.

2. Build and install on an Android TV device/emulator (API 24+):

   ```bash
   ./gradlew :app:installDebug
   ```

## Maestro

With a TV emulator running (e.g. the `GoogleTV_1080p` AVD):

```bash
maestro test .maestro/home.yaml
```

## Layout

```
app/src/main/java/com/jedflix/tv/
  JedflixTvApp.kt        # Application: Coil image loader, TMDB client/repository
  MainActivity.kt
  data/tmdb/             # Retrofit API, DTOs, mapper, shelves, repository
  ui/theme/              # TV Material dark theme, inline icons
  ui/splash/             # JEDFLIX wordmark launch animation
  ui/navigation/         # NavHost: splash -> catalog sections
  ui/home/               # CatalogScreen, CatalogViewModel, CatalogUiState
  ui/components/         # NavRail, Billboard, CatalogRow, PosterCard, CatalogSkeletons
```
