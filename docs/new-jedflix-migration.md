# Moving to the Stremio-based Jedflix app

The legacy app is `com.jedflix.tv`; the new Jedflix is `com.jedflix.stremio`.
Stock Stremio remains `com.stremio.one`. All three can stay installed together.
Version 0.9.3 changes migration to the separate package and retains signature
and release validation before installing or opening the app.

Migration reads `jedflix-app-update.json` from the latest stable GitHub release
of `JedBorseth/jedflix-tv-stremio`, chooses the supported ABI, validates package,
version, size, hash, native libraries, and the pinned signing certificate, then
opens Android's installer. New app files are `jedflix-app-VERSION-ABI.apk`.
The fork's original `jedflix-update.json` and `jedflix-VERSION-ABI.apk` files are
reserved for signed handoff updates of older fork installs; this legacy bridge
never selects those files.

The old app and Stremio keep their private data. History, My List and Real-Debrid
settings are not imported into the new app yet. Configure Real-Debrid there and
keep the old app until onboarding succeeds. New Jedflix self-updates preserve
its own private settings and Android Keystore normally.

If a different signer occupies `com.jedflix.stremio`, the bridge refuses to open
it and offers its Android settings. Stock Stremio does not trigger that conflict.

Versions 0.9.1 and 0.9.2 targeted `com.stremio.one`: update legacy Jedflix to 0.9.3
before migrating. Stock Stremio does not need to be removed.

## Regression verification

Build `:app:assembleDebug :app:assembleDebugAndroidTest` and install both APKs on a
disposable emulator. Run the corresponding fixture check:

```bash
JEDFLIX_DEVICE_HOST=local python3 scripts/verify-new-jedflix-migration.py \
  --adb /path/to/jedflix-tv-stremio/scripts/adb.sh \
  --serial emulator-5556 --fixture stock
```

`stock` requires Stremio installed and the separate Jedflix absent; it must show
migration normally. `absent` also expects the separate app absent. `jedflix`
requires a signed current release and asserts handoff to Home. `foreign` requires
a foreign-signed APK using the new ID and asserts the conflict screen.

The identity test uses the real Android package manager. The UI check starts the
legacy launcher and asserts its destination. `--ui-only` checks an optimized
release APK after installation. The verifier never uninstalls apps or clears data.
