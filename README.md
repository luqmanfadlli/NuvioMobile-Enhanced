<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <h1>Nuvio Enhanced</h1>

  <p>
    An unofficial fork of <a href="https://github.com/NuvioMedia/NuvioMobile">Nuvio Mobile</a> that keeps pace with
    upstream and adds the features and platform polish on top of it.
    <br /><br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  <p>
    <a href="https://github.com/luqmanfadlli/NuvioMobile-Enhanced/releases/latest">Releases</a> ·
    <a href="https://github.com/NuvioMedia/NuvioMobile">Upstream project</a> ·
    <a href="https://nuvio.tv">nuvio.tv</a> ·
    <a href="https://nuvio.tv/support">Support Nuvio</a>
  </p>

</div>

> **Unofficial.** This fork is not affiliated with or supported by the Nuvio team. Please report bugs you find here to
> **this** repository, not upstream — unless you can reproduce them on an official build too.

---

## Install

### iOS — AltStore / SideStore

Add this source, then install **Nuvio Enhanced** from it:

```
https://github.com/luqmanfadlli/NuvioMobile-Enhanced/raw/refs/heads/enhanced/store.json
```
or grab the IPA from [the latest release](https://github.com/luqmanfadlli/NuvioMobile-Enhanced/releases/latest).

### Android

Grab the APK from [the latest release](https://github.com/luqmanfadlli/NuvioMobile-Enhanced/releases/latest).

The app installs as **Nuvio Enhanced**. It shares the `com.nuvio.app` application ID with the official Android build,
so the two cannot be installed side by side — uninstall one first.

---

## What Enhanced adds

Everythings below are added on top of upstream Nuvio Mobile.

### Home

| Feature | Where | Default |
|---|---|---|
| **Hero trailer autoplay** — trailers play in the hero carousel instead of static artwork. The carousel stops auto-advancing while a trailer plays, so it only moves when you swipe. | Settings → Layout → Home Layout → **Hero Trailer Playback** | Off |
| **Trailer start delay** — how long the artwork holds before the trailer starts, `Instant` to 10 s. Appears once trailer playback is on. | Settings → Layout → Home Layout → **Trailer Start Delay** | Instant |
| **Hero style** — `Full-bleed` (artwork spans the screen) or `Card` (rounded, inset). | Settings → Layout → Home Layout → **Hero Style** | Full-bleed |
| **Dynamic background** — tints the home screen with a gradient pulled from the featured artwork's colours. | Settings → Layout → **Dynamic background color** | Off |
| **Catalog accent underline** — accent rule under each catalog row heading. | Settings → Layout → **Catalog accent underline** | Off |

### Player

| Feature | Where | Default |
|---|---|---|
| **Tap-to-seek on the timeline** — tap anywhere on the progress bar to jump there. | — | Always on |
| **Volume Boost** — volume can be boosted past 100% | Swipe up all the way past 100% | Always on |
| **Stream Quality Chooser** - add quality indicator and ability to choose quality on HLS stream whenever available | Player screen overlay | Best quality supported by hardware |
| **Info Button** add playback info button to show currently playing video and audio information | Player screen overlay | — |
| **Swipe to Seek toggle** — an option to turn it off to prevent accidental seeking while keeping the up/down brightness and volume swipes. | Settings → Playback → **Swipe to Seek** (under Touch Gestures) | On |
| **Hardware keyboard shortcuts** — <kbd>Space</kbd> play/pause, <kbd>←</kbd> / <kbd>→</kbd> seek 10 s, <kbd>Esc</kbd> leave the player. Inert while a panel is open or the controls are locked. | — | Always on |
| **Adjustable subtitle transparency** | Settings → Playback → Subtitle Rendering → **Background Color** | — |

### Live TV

Upstream Nuvio has no Live TV. This fork adds the whole feature.

| Feature | Where | Default |
|---|---|---|
| **M3U playlists** | Settings → Integrations → Live TV → **Playlists** | — |
| **Xtream** — connect with a server URL, username and password. | Settings → Integrations → Live TV → **Providers** → Xtream | Not configured |
| **Stalker Portal** — connect with a portal URL and MAC address; login details optional. | Settings → Integrations → Live TV → **Providers** → Stalker Portal | Not configured |
| **Show Live TV in navigation** — the tab appears once at least one source is configured. | Settings → Integrations → Live TV → **Show Live TV in navigation** | On |

### Profiles

| Feature | Where | Default |
|---|---|---|
| **Profile Insights** — activity, library and taste breakdowns for the active profile, in Overview and Taste sections. | Settings → **Profile** | Always available |
| **Custom profile background** — point a profile at any `http(s)` image URL. | Edit Profile → **Choose Profile Background** → Custom → **Custom background URL** | None |

### Details & discovery

| Feature | Where | Default |
|---|---|---|
| **More Like This → View All** — the recommendation rail's header opens the full list as a paged grid that keeps loading as you scroll, instead of stopping at one page. | — | Always on when the rail has more to show |
| **Budget and revenue** — added to the details block for movies. | Shown with Settings → Layout → Detail Page → **Details** | — |
| **Episode ratings** — TMDB vote averages on episode cards. | Settings → Content & Discovery → TMDB Enrichment → **Episode ratings** | — |
| **Random Episode** — Play random episode for series. | 3 dots next to play button → **random icon** | — |
| **Include watched episodes toggle** — Include watched episodes in random playback. | Settings → Playback → **Include watched episodes in random playback**| Off |

### Library

| Feature | Where | Default |
|---|---|---|
| **Library Calendar** — show release date of ongoing series in the library. | Library screen → **calendar icon toggle** | — |

### Downloads

| Feature | Where | Default |
|---|---|---|
| **Wi-Fi-only downloads** — downloads wait for Wi-Fi unless you allow mobile data. The switch sits at the top of the Downloads screen, not in Settings. | Downloads screen → **Allow mobile data** | Off (Wi-Fi only) |
| **Custom download location** — pick any folder to save downloads to (a folder you grant via Storage Access Framework on Android, or via the Files app on iOS) instead of the app's private storage. | Downloads screen → **gear icon** → **Download Location** | Internal storage |
| **Background downloads (iOS)** — a download keeps transferring after you leave the app, instead of stalling the moment it's backgrounded. Doesn't survive a full force-quit. | — | Always on |
| **Download progress notification (iOS)** — live progress shown in a Lock Screen/Dynamic Island Live Activity while a download is running. | — | Always on |

### Tracking

| Feature | Where | Default |
|---|---|---|
| **Sign in with a code** — device-code sign-in for **Trakt** and **SIMKL**, for when the browser redirect will not come back, especially for installation within LiveContainer. Shows a code to enter on any other device. | Settings → Tracking → provider card → **Connect with code** | — |

### iOS look and feel

| Feature | Where | Default |
|---|---|---|
| **Experimental Picture in Picture** *(iOS)* — Metal-based render pipeline that slides into PiP without reopening the stream. It changes the core video output, so treat it as experimental. | Settings → Playback → **Experimental Picture in Picture** | Off |
| **Morphed Liquid Glass tab bar** — shrinks to a compact pill, with native drag-across-tabs and the system glass highlight (requires an **iPhone on iOS 26 or newer**). | Settings → Layout → **Liquid Glass tab bar** | Morphed |
| **Skia graphics engine** — rebuild graphics engine for animated artwork rendering with shared codecs and bounded memory. Fixed crash on large animated collection, support animated avatar and badge | — | — |
| **Bundle CJK Font** — fixed Chinese subtitle rendering | — | — |

---

## Build from source

```bash
git clone https://github.com/luqmanfadlli/NuvioMobile-Enhanced.git
cd NuvioMobile-Enhanced
git checkout enhanced
```

### Android

Requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug        # sideload flavour
./gradlew :androidApp:assemblePlaystoreDebug   # store flavour
```

### iOS

Requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

## Staying in sync

This fork tracks `upstream/cmp-rewrite` and merges upstream releases as they land. If you are working on the fork:

```bash
git remote add upstream https://github.com/NuvioMedia/NuvioMobile.git
git fetch upstream
git merge upstream/cmp-rewrite
```

---

## Credits

Nuvio is built by [NuvioMedia](https://github.com/NuvioMedia) — all credit for the app itself belongs to them and its
contributors. This repository only adds to their work. If you enjoy Nuvio, [support the upstream project](https://nuvio.tv/support).

## License

[GNU General Public License v3.0](./LICENSE) — same as upstream.
