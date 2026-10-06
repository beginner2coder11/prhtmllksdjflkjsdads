# MyApp — Web2App runtime

A standalone Android WebView app whose entire behaviour is driven by a single
config file: [`app/src/main/assets/app_settings.json`](app/src/main/assets/app_settings.json).
This is the **build target** for the no-code builder: the builder produces the
config + assets, you commit them here, and GitHub Actions builds the APK.

## How it works

- **`SplashActivity`** (launcher) reads `app_settings.json`, shows the configured
  splash (background colour + image + optional loader), then opens `MainActivity`.
- **`MainActivity`** loads the website in an advanced `WebView` (cookies, JS, popups,
  pull-to-refresh, no-internet overlay) and renders the optional bottom tab bar.

No code changes are needed to rebrand the app — only the config and a few resources.

## What the builder controls

### `app_settings.json` (runtime behaviour)
| Field | Meaning |
|-------|---------|
| `appName` | Display name (also set as the `app_name` string / manifest label). |
| `websiteUrl` | The URL loaded on launch (and the default tab URL). |
| `themeColor` | App theme colour. |
| `splash` | `{ color, image (base64), size, loader }` shown on launch. |
| `showTabbar` | Show the bottom navigation bar. |
| `tabbars[]` | `{ icon, title, url }` per tab. `icon` is `material:<key>`, `lucide:<name>`, or a base64 data-URI. |
| `tabSettings` | `{ tabActiveColor, tabInactiveColor, tabBarColor, showTitles }`. |
| `permissions[]` | Runtime permissions to request: `0`=location, `1`=file/camera, `2`=audio/video. |

### Build-time identity (set per app before building)
- **Package name** → `applicationId` / `namespace` in [`app/build.gradle.kts`](app/build.gradle.kts).
- **App name** → `app_name` in `app/src/main/res/values/strings.xml`.
- **Launcher icon** → `app/src/main/res/drawable/app_icon.png`.
- **Version** → `versionName` / `versionCode` in `app/build.gradle.kts`.

## Building

### GitHub Actions (recommended)
Push to `main`; the workflow in [`.github/workflows/build.yml`](.github/workflows/build.yml)
builds `app-debug.apk` and uploads it as a downloadable artifact. No SDK setup needed.

### Locally
```bash
# Requires Android SDK; create local.properties with: sdk.dir=/path/to/Android/sdk
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Stack
Kotlin · XML Views (no Compose) · AppCompat 1.7 · Material 1.12 · minSdk 21 · targetSdk/compileSdk 34 · Gradle 8.14 / AGP 8.3.2.
