# Vault Gallery (Android)

Native Kotlin / Jetpack Compose rebuild of the Vault Gallery web app (emerald / jade / gold, dark + light, glass surfaces).
No WebView. Local-first: **no INTERNET permission**, no analytics, no uploads.

## Build the APK from your phone (GitHub Actions)
1. Create a GitHub repo and upload this folder's contents (the `.github` folder must be included).
2. Open the **Actions** tab, pick **Android Build**, tap **Run workflow** (it also runs on every push).
3. When it finishes, open the run and download the artifact **vault-gallery-debug.apk** (a zip containing the APK).
4. Unzip, install the APK (allow "install unknown apps" for your browser/files app).

### Signed release APK (optional)
Add these repository secrets; the workflow then also uploads **vault-gallery-release.apk**:
`KEYSTORE_BASE64` (base64 of your .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Nothing secret is ever committed. Without them only the debug APK is built.

## Requirements
Android 11+ (minSdk 30). This removes legacy storage permissions and lets deletes use the system confirmation dialog.

## What is implemented
- MediaStore indexing into Room (batched, background, live updates via ContentObserver), Android 13/14 photo permissions with explanation + open-settings
- Grid (1/2/3 columns) and masonry layouts, search (name/caption/tag), tags (create/rename/delete/bulk assign), favorites, sorting
- Full-screen viewer: swipe, pinch/double-tap zoom, info, share (Sharesheet), favorite, trash, slideshow (2/3/5/10 s with progress)
- Non-destructive editor: 8 web presets, brightness/contrast/saturation/sepia/blur, rotate, flip, undo/redo, revert, **Save as copy** (original untouched)
- Trash with restore and permanent delete (system confirmation required), recently viewed, photo of the day (local, deterministic)
- Wallet: Stars/Coins with ledger table, atomic non-negative spending, unlock flow; test credits exist only in DEBUG builds
- App lock: PIN (PBKDF2 + Keystore-sealed verifier, lockout backoff), biometrics, auto-lock timeout, lock-now, recents/screenshot protection (FLAG_SECURE)
- Auto Backup / device transfer disabled

## Not implemented yet (next phases)
Encrypted private storage (locked photos are currently gated by app logic and are never decoded while locked, but files are not encrypted at rest),
duplicate detection, statistics screen, backup export/import (+ encrypted backup), storage screen, Photo Picker import, Paging 3, crop/straighten,
web localStorage JSON import, WorkManager jobs. Lint currently reports without failing the build (`abortOnError=false`).

## Behaviour notes
- In the web app every imported photo was locked by default. On Android that would lock your whole camera roll, so photos are **unlocked by default** and you lock them via selection mode.
- Blur in the editor preview needs Android 12+; saved copies apply blur on all versions.
- This project has not been compiled yet. The first CI run is the first compile; expect to iterate on any errors it reports.
