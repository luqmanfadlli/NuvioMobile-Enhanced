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

---

## What Enhanced adds

Everything below is added on top of upstream Nuvio Mobile. Features that upstream has since adopted are dropped from this list.

### Home

| Feature | Where | Default |
|---|---|---|
| **Hero trailer autoplay** — trailers play in the hero carousel instead of static artwork. The carousel stops auto-advancing while a trailer plays, so it only moves when you swipe. | Settings → Layout → Home Layout → **Hero Trailer Playback** | Off |
| **Trailer start with sound** — start the hero trailer unmuted instead of muted. You can still mute it with the speaker button. Appears once trailer playback is on. | Settings → Layout → Home Layout → **Start with sound** | Off |
| **Trailer start delay** — how long the artwork holds before the trailer starts, `Instant` to 10 s. Appears once trailer playback is on. | Settings → Layout → Home Layout → **Trailer Start Delay** | Instant |
| **Hero style** — `Full-bleed` (artwork spans the screen) or `Card` (rounded, inset). | Settings → Layout → Home Layout → **Hero Style** | Full-bleed |
| **Dynamic background** — tints the home screen with a gradient pulled from the featured artwork's colours. | Settings → Layout → Home → **Dynamic background color** | Off |
| **Catalog accent underline** — accent rule under each catalog row heading. | Settings → Layout → Home → **Catalog accent underline** | Off |
| **Smart hero framing** — when a wide backdrop is cropped to fit the hero, the visible part is chosen to keep the subject in frame instead of always cutting to the centre. | — | Always on |
| **Accent gradient on more surfaces** — the theme's accent gradient also colours settings and Profile Insight icons, Taste DNA bars and chips, the catalog accent underline, primary buttons, and the tinted iOS tab bar in every tab bar mode. | Settings → Layout → **Theme** | Follows the selected theme |

### Appearance

| Feature | Where | Default |
|---|---|---|
| **App background** — set a photo from your gallery or any image URL (JPG, PNG, GIF or WebP) as the background behind every screen. | Settings → Appearance → **Background** | Default |
| **Glass UI** *(experimental)* — cards, sheets, dialogs and headers become frosted glass over the app background, with `Dark`, `Balanced` and `Vivid` styles. | Settings → Appearance → **Glass UI** | Off |
| **More posters in landscape** — Discover and collection folders show 6 posters per row in landscape on phones and 7 on tablets. | — | Always on |

### Player

| Feature | Where | Default |
|---|---|---|
| **Tap-to-seek on the timeline** — tap anywhere on the progress bar to jump there. | — | Always on |
| **Volume Boost** — volume can be boosted past 100% | Swipe up all the way past 100% | Always on |
| **Stream Quality Chooser** — add quality indicator and ability to choose quality on HLS stream whenever available, in both the legacy and the new control layout | Player screen overlay | Best quality supported by hardware |
| **Info Button** — add playback info button to show currently playing video and audio information, in both the legacy and the new control layout | Player screen overlay | — |
| **Picture in Picture button** — enter PiP straight from the player overlay, in both the legacy and the new control layout. | Player screen overlay | — |
| **Gesture readouts** — swiping shows the seek target and delta, and the volume/brightness percentage, in the new control layout as well. | — | Always on |
| **Swipe to Seek toggle** — an option to turn it off to prevent accidental seeking while keeping the up/down brightness and volume swipes. | Settings → Playback → **Swipe to Seek** (under Touch Gestures) | On |
| **Hardware keyboard shortcuts** — <kbd>Space</kbd> play/pause, <kbd>←</kbd> / <kbd>→</kbd> seek, <kbd>↑</kbd> / <kbd>↓</kbd> volume, <kbd>Esc</kbd> leave the player. Inert while a panel is open or the controls are locked. | — | Always on |
| **Adjustable subtitle transparency** | Settings → Playback → Subtitle Rendering → **Background Color** | — |
| **Subtitle shadow** — a soft shadow behind subtitle text, with adjustable strength, to keep it readable on bright scenes. | Settings → Playback → Subtitle Rendering → **Shadow** | Off |
| **Outline blur** — softens only the subtitle outline while the text stays sharp. | Settings → Playback → Subtitle Rendering → **Outline blur** | Off |
| **Subtitle style per device** — subtitle size, colours, outline and position are kept per device, so a phone and a tablet on the same profile can look different. | — | Always on |
| **Custom subtitle fonts** — import a `.ttf` or `.otf` file and use it for subtitles. Fonts stay on the device; subtitles in non-Latin scripts keep a font that supports them. | Settings → Playback → Subtitle Rendering → **Font** → **Import Font** | Default |
| **Hebrew subtitle punctuation fix** *(iOS)* — external Hebrew subtitles authored for left-to-right renderers get their punctuation moved back to the end of the sentence. Correctly authored files are left untouched. | — | Always on |
| **Sync subtitles by ear** — tap **Heard** when you hear a line and **Saw** when its subtitle appears, in either order, and the offset is set for you. | Player → Subtitles panel → **Sync by ear** | — |
| **Buffered range on the legacy seek bar** — the legacy control layout's timeline shows how far ahead the stream is buffered, like the new layout does. | — | Always on |
| **Seek bar thumbnails** — a preview frame floats above the seek bar while you drag it, so you can see where you will land. Frames are grabbed from the stream itself (stream headers and extension-less HLS included) only around scrubbing: the connection opens shortly after the controls appear and is released after a short idle. | Settings → Playback → **Seek Bar Thumbnails** | On |
| **Player buttons** — choose and order the player buttons. In the new layout, pick which buttons stay on the bar and which sit behind the arrow. In the legacy layout, reorder or hide the top and bottom bar buttons; hidden buttons are not shown at all. | Settings → Playback → **Player buttons** (new layout) / **Top bar buttons** and **Bottom bar buttons** (legacy layout) | All buttons in the usual order |
| **Keeps playing behind Notification Center** *(iOS)* — pulling down Notification Center no longer pauses the video: audio keeps going and the picture picks up right where it is when you come back. If you stay away for more than 10 seconds, playback pauses as before. | — | Always on |
| **Movie recommendations** — in the last four minutes of a movie, a small card suggests More Like This titles you haven't watched, from your chosen More Like This source (TMDB, Trakt or SIMKL, falling back to TMDB). Dismissing it snoozes it for a minute. | Settings → Playback → **Movie Recommendations** | On |
| **Night mode** — a player button that evens out loud and quiet scenes for listening at a low volume, on every engine including iOS. | Settings → Playback → Audio → **Night Mode Button** | Button shown, night mode off |
| **Anime4K** *(Android)* — a 2x upscaler and a line sharpener for anime, on ExoPlayer and libmpv. Only applied to anime. | Settings → Playback → Video → **Anime 2x Upscaler** / **Anime Line Sharpener** | Off |
| **Plain controls** — hide the glass backgrounds behind player buttons and time pills, and hide the skip back/forward buttons. | Settings → Playback → Player interface → **Glass Backgrounds on Controls** / **Show Skip Buttons** | On |
| **Title logo in the player** — show the title artwork instead of the title text in the player controls. | Settings → Playback → Player interface → **Show title logo** | Off |
| **Skip Intro timing** — show the Skip Intro button earlier or later than the intro timestamp. | Settings → Playback → Skip segments → **Skip Intro Timing** | On time |
| **Download speed while loading** — the loading screen shows how fast the stream is downloading. | — | Always on |
| **Mini player** *(Android)* — keep watching in a small floating window while you browse. Tap it to return to full screen. With it on, the player's back button still ends playback; use the **PiP** button in the player header (or the system back gesture) to minimize. | Settings → Playback → **Mini Player** | Off |
| **Private viewing** — play without saving watch progress, Continue Watching or watched status, and without syncing playback to connected trackers. | Settings → Playback → **Private Viewing** | Off |

### Live TV

Upstream Nuvio has no Live TV. This fork adds the whole feature.

| Feature | Where | Default |
|---|---|---|
| **M3U playlists** | Settings → Integrations → Live TV → **Playlists** | — |
| **Xtream** — connect with a server URL, username and password. | Settings → Integrations → Live TV → **Providers** → Xtream | Not configured |
| **Stalker Portal** — connect with a portal URL and MAC address; login details optional. | Settings → Integrations → Live TV → **Providers** → Stalker Portal | Not configured |
| **TV guide (EPG)** — now/next programmes in the channel list and the player, plus a full guide per channel. XMLTV guides linked in playlists and from Xtream/Stalker are found automatically; you can also add your own `.xml` or `.xml.gz` URLs. | Settings → Integrations → Live TV → **TV Guide (EPG)** | Auto-detect on |
| **Quality chooser** — pick a quality on HLS channels that offer several; Auto stays the default. | Player screen overlay | Auto |
| **Category search** — Live TV search also matches channel categories. | Live TV → search | Always on |
| **Show Live TV in navigation** — the tab appears once at least one source is configured. | Settings → Integrations → Live TV → **Show Live TV in navigation** | On |

### Profiles

| Feature | Where | Default |
|---|---|---|
| **Profile Insights** — activity, library and taste breakdowns for the active profile, in Overview and Taste sections. | Settings → **Profile** | Always available |
| **Custom profile background** — point a profile at any `http(s)` image URL. | Edit Profile → **Choose Profile Background** → Custom → **Custom background URL** | None |
| **Biometric unlock** — unlock the PIN-locked primary profile with fingerprint or Face ID at launch and when switching profiles. The PIN stays available as a fallback. Biometric data never leaves the device; adding or removing a fingerprint or face turns it off until you re-enable it with your PIN. | Edit Profile (primary profile, PIN lock on) → **Biometric unlock** | Off |

### Details & discovery

| Feature | Where | Default |
|---|---|---|
| **More Like This → View All** — the recommendation rail's header opens the full list as a paged grid that keeps loading as you scroll, instead of stopping at one page. | — | Always on when the rail has more to show |
| **Episode navigator** — a sticky bar above the episode list of every season to search episodes by title, plot or number, jump straight to an episode number, and sort (first to last, last to first, highest rated, newest aired, unwatched first). | Details page → episode list | Always on for series |
| **Progress on episode cards** — partially watched episodes show a progress bar on their card. | — | Always on |
| **Original-language titles** — show titles, title logos, posters and backdrops in the title's original language, falling back to your TMDB language when unavailable. | Settings → Content & Discovery → TMDB Enrichment → **Original language** | Off |
| **Budget and revenue** — added to the details block for movies. | Shown with Settings → Layout → Detail Page → **Details** | — |
| **Icon action row** — play/resume spans the full width and the other actions (download, start from the beginning, random episode, external player, watched, library) sit as icons underneath it, instead of hiding behind the 3-dots menu. Turn it off to get the old row back. | Settings → Layout → Detail Page → **Icon action row** | On |
| **Download button** — adds a download button under Play that opens the stream list in download mode. | Settings → Downloads → **Show download button** | On |
| **Trailer start with sound** — start the details-page trailer unmuted. Appears once trailer playback is on. | Settings → Layout → Detail Page → **Start with sound** | Off |
| **In-app ratings** — rate a movie, show, season or episode from 1 to 10 on the details page. The rating syncs to Trakt, SIMKL, MDBList, and/or AniList. | Details page → star row under the actions → **Rate** | — |
| **Library icon on saved items** — the library action shows when a title is already saved. | — | Always on |
| **Desktop hero on tablets** — tablets use the wide desktop details hero with a reworked action layout. | — | Always on on tablets |

### Streams

| Feature | Where | Default |
|---|---|---|
| **Pinned sources** — pin an addon or a scraper source so its results always sort to the top of the stream list. Pins are kept per profile. | Stream list → long-press a stream or its source header → **Pin to top** | None pinned |
| **Search bar** — a filter field above the stream list that narrows the results as you type, matching every term against the stream name, description, source, addon, filename and badges — so `1080 hevc multi` works. | Settings → Layout → Streams → **Search bar** | Off |

### Library

| Feature | Where | Default |
|---|---|---|
| **Library Calendar** — show release date of ongoing series in the library. | Library screen → **calendar icon toggle** | — |
| **WeTrakr Library** — browse your WeTrakr lists as a Library source once WeTrakr is connected. | Library screen → source picker → **WeTrakr** | — |
| **AniList Library** — browse your AniList anime lists as a Library source once AniList is connected. | Library screen → source picker → **AniList** | — |
| **PublicMetaDB Library** — browse your PublicMetaDB lists as a Library source once PublicMetaDB is connected. | Library screen → source picker → **PublicMetaDB** | — |

### Downloads

| Feature | Where | Default |
|---|---|---|
| **Wi-Fi-only downloads** — downloads wait for Wi-Fi unless you allow mobile data. The switch sits at the top of the Downloads screen, not in Settings. | Downloads screen → **Allow mobile data** | Off (Wi-Fi only) |
| **Custom download location** — pick any folder to save downloads to (a folder you grant via Storage Access Framework on Android, or via the Files app on iOS) instead of the app's private storage. | Downloads screen → **gear icon** → **Download Location** | Internal storage |
| **Background downloads (iOS)** — a download keeps transferring after you leave the app, instead of stalling the moment it's backgrounded. Doesn't survive a full force-quit. | — | Always on |

### Tracking

| Feature | Where | Default |
|---|---|---|
| **AniList** — connect your AniList account to sync anime watch progress, watched episodes, list status (Watching, Planning, Completed, Paused, Dropped, Rewatching) and scores. AniList can also be the source for Continue Watching and the Library. Titles are matched to AniList through IMDb, MAL, Kitsu and AniDB IDs. Connected per profile. | Settings → Tracking → **AniList** → **Connect AniList** | Not connected |
| **WeTrakr** — connect your WeTrakr account to sync lists, watch history, ratings, scrobbles and Continue Watching. WeTrakr can also be the source for the Library (Planning, Watching, Waiting, Watched, Paused, Dropped). | Settings → Tracking → **WeTrakr** → **Connect WeTrakr** | Not connected |
| **PublicMetaDB** — connect with your own PublicMetaDB API key to sync scrobbles, ratings, personal lists and watched history. PublicMetaDB can also be the source for the Library. | Settings → Tracking → **PublicMetaDB** | Not connected |
| **Sign in with a code** — device-code sign-in for **Trakt** and **SIMKL**, for when the browser redirect will not come back, especially for installation within LiveContainer. Shows a code to enter on any other device. | Settings → Tracking → provider card → **Connect with code** | — |

### Navigation & settings

| Feature | Where | Default |
|---|---|---|
| **Floating nav bar on iPad and tablets** — the jelly floating pill, with inline labels on tablets. | Settings → Layout → navigation bar style | — |
| **Blurred bottom sheets** *(Android)* — bottom sheets blur the screen behind them, as on iOS. | — | Always on |
| **Nav bar at the top** — move the floating nav bar to the top of the screen. | Settings → Layout → navigation bar → **Position** | Bottom |
| **DNS over HTTPS** — pick an encrypted DNS provider for all of Nuvio's connections (addons, metadata, images, sync, trackers and the ExoPlayer engine). The rest of the device keeps its own DNS. libmpv and web sign-in pages still use the system DNS. | Settings → Advanced → **DNS** | System default |
| **Discord Rich Presence** — show what you're watching on your Discord profile, on Android and iOS. | Settings → Integrations → **Discord** | Off |

### iOS look and feel

| Feature | Where | Default |
|---|---|---|
| **Morphed Liquid Glass tab bar** — shrinks to a compact pill, with native drag-across-tabs and the system glass highlight (requires an **iPhone on iOS 26 or newer**). | Settings → Layout → **Liquid Glass tab bar** | Morphed |
| **Skia graphics engine** — rebuild graphics engine for animated artwork rendering with shared codecs and bounded memory. Fixed crash on large animated collection, support animated avatar and badge | — | — |
| **Bundle CJK Font** — fixed Chinese subtitle rendering | — | — |
| **Subtitles in Picture in Picture** — text subtitles keep showing in Picture in Picture and the mini player. | — | Always on |
| **Dolby Vision in Picture in Picture** — Dolby Vision Profile 5 videos keep their colours in Picture in Picture instead of turning purple and green. | — | Always on |

### Performance

- **Faster startup** *(Android)* — saved data is loaded in the background while the splash screen is up.
- **Instant home** — the last home screen is saved per profile and shown on the next launch while fresh rows load.
- **Lighter Live TV** — channels load only when you open Live TV, sources download in parallel, and the guide is cached for up to 6 hours.
- **Smoother artwork colours** — dynamic background and details-page colours appear without a dark first frame and blend straight from one hero to the next.
- **Faster details** — movie and show details open sooner.
- **Smoother scrolling** — home, shelves and grids scroll with fewer dropped frames.
- **Lower memory on iPhone** — long sessions and 4K HDR / Dolby Vision playback use less memory, so iOS is less likely to close the app.
- **Libass rendering off the main thread** *(Android, ExoPlayer)* — ASS/SSA subtitles render on their own thread for less stutter with heavy styled subtitles.

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
