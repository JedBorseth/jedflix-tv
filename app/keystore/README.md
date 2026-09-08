# Upload keystore

This keystore signs every GitHub APK so in-app updates can replace the installed app.

Do not regenerate it. A new cert cannot update an existing install; users would have to uninstall first.

- File: `jedflix-upload.jks`
- Alias: `jedflix`
- Store / key password: `jedflix-upload`

Debug and release both use this config (`assembleDebug` is what GitHub Releases ship).
The cert matches the v0.3.1 GitHub APK (`SHA-256 6ebef2bc…4486b`).
