# Moving to the Stremio-based Jedflix app

The legacy app is `com.jedflix.tv`; the new Jedflix is `com.stremio.one`.
Migration downloads the latest stable release from `JedBorseth/jedflix-tv-stremio`,
checks the manifest, ABI, package, version and pinned signing certificate, and
opens Android's installer. The old app remains installed and its data is kept.
History, My List and Real-Debrid settings are not imported into the new app.

Stock Stremio uses the same package as the new Jedflix, with a different signing
key. The apps cannot coexist. Version 0.9.1 checked only the package ID before
forwarding and could open stock Stremio's QR login instead of migrating.

Version 0.9.2 checks the installed app's signing certificate and version before
forwarding, including when returning from Android's installer. A conflicting
install shows an explanation and opens its Android app settings on request.
Uninstalling Stremio removes its local app data; this remains an explicit user
action. Returning to the bridge after removal shows the normal migration screen.
Users may also keep using legacy Jedflix.

On 0.9.1, recover by uninstalling **stock Stremio**, then reopening **legacy
Jedflix** and completing migration. The published Jedflix 1.0.2 ARM64 APK already
bypasses Stremio login and reaches Home on a clean installation.

## Regression verification

Build `:app:assembleDebug :app:assembleDebugAndroidTest` and install both APKs on a
disposable emulator. Prepare `com.stremio.one` as stock Stremio, absent, or the
signed Jedflix release, then run the corresponding fixture check:

```bash
JEDFLIX_DEVICE_HOST=local python3 scripts/verify-new-jedflix-migration.py \
  --adb /path/to/jedflix-tv-stremio/scripts/adb.sh \
  --serial emulator-5556 --fixture stock
```

Repeat with `--fixture absent` and `--fixture jedflix`. A Jedflix release re-signed
with a disposable foreign certificate should also pass the `stock` check: a high
version code alone must not make an app trusted. The identity test runs in the
actual Android package manager; the UI check starts the legacy launcher and
asserts its destination. `--ui-only` checks the optimized release APK after it is
installed. The verifier never uninstalls apps or clears data.
