# BannerTV architecture (revised 2026-11-07)

**Goal:** one master app. Nuvio Enhanced is the shell and owns all IPTV and entertainment logic. The Sports hub (screens, sports
data, matching, follows, reminders) is ported from homelessbrian's NuvioPlusIPTV-Mobile. News and podcasts come from BannerTV
mobile. This replaces the first plan, which put BannerTV's sports providers and stream linker underneath Nuvio. That work is
parked (see "Parked work").

## Ownership

| Area | Owner | Status |
| --- | --- | --- |
| Movies, shows, addons, library, trackers, profiles, player | Nuvio Enhanced (unchanged) | Working upstream |
| IPTV: playlists, Xtream/Stalker, XMLTV, channels, favourites | Nuvio Enhanced `features/livetv` (unchanged) | Working upstream |
| Sports data: feeds, standings, catalog, follows, event identity | Ported from NuvioPlusIPTV-Mobile into `features/sports` | Compiles on Android. Never run |
| Sports hub screens: My Sports, All Events, Search, Following, League Networks, game page, league page | Same | Compiles on Android. Never run |
| Sports-to-channel matching | Ported `SportsMatcher`, reading Nuvio's guide through an adapter | Compiles. Never run against real data |
| Game reminders and live-score notifications | Ported, Android only | Compiles. iOS is a no-op |
| News | BannerTV `shared` (`NewsRepository`, ranking, RSS catalog) | Not started |
| Podcasts | BannerTV (`TeamPodcastResolver` in `shared`, Android screens in `mobile`) | Not started |

## How the ported Sports hub plugs into Nuvio

```
features/sports/                 ported from NuvioPlusIPTV-Mobile features/livetv/sports   (package com.nuvio.app.features.sports)
features/sports/ui/              ported from features/livetv/ui/sports
features/sports/compat/          the seam: what the ported code used to get from HBM's Live TV layer
    NuvioIptvBridge.kt           LiveTvRepository, EpgStore, LiveTvPlayback, NowNext: read Nuvio's own channels and guide
    LiveTvPreferences.kt         the hub's own saved follows and settings (files), defaults for the rest
    LiveTvFiles / SportsKeyValue / SportsAlarmPlatform   small per-platform actuals
    LiveTvModels, CatchupUrlBuilder, LiveTvDeepLinks, LiveTvAlarms, ParentalControls, VideoPlaying, SportsUiHelpers   copied or trimmed
```

- **One guide, one channel list.** `compat/NuvioIptvBridge.kt` maps Nuvio's `LiveTvChannel` and `LiveTvEpgProgram` to the types the
  ported code expects, on demand. Nothing is fetched, parsed or stored twice. The guide window is whatever Nuvio keeps in memory
  (3 hours back, 48 hours ahead), so matching reaches no further than that.
- **Package split.** HBM's code lived at `com.nuvio.app.features.livetv`, which is also where Nuvio Enhanced keeps its own Live TV.
  The ported code is in `features.sports` so neither tree overwrites the other and later syncs can be diffed.
- **Entry point.** A Sports icon in the Live TV header opens `SportsRoute`. Game and league pages have their own routes. Notification
  and deep links of the form `nuvio://livetv/sports[?event=...]` and `nuvio://livetv/channel?key=...` open them.
- **Playing a channel** from the hub goes through Nuvio's existing `onLiveTvChannelClick`, so playback is Nuvio's player.

## What was deliberately left out of the port

| Left out | Why |
| --- | --- |
| Recording (`record/*`, the Record button, recording service) | Depends on HBM's playlist model, Xtream client and VPN backup. Not requested. Needs its own design |
| Google Drive and fork-author cloud sync | Connects users to infrastructure the fork author runs and uses embedded credentials. Not enabled by default (see `LICENSE_COMPLIANCE.md`) |
| Parental controls, hidden channels and groups, per-channel quality history | Nuvio has none of these. Compat returns the "off/empty" values, which is the real behaviour of a build without them |
| Catch-up from the sports page | Nuvio Enhanced's Live TV has no catch-up. The button is not shown (no catch-up info on mapped channels) and the action shows a message |
| HBM's per-game playback failover | Hooks exist (`LiveTvPlayback.gameFailover`) but are not connected to Nuvio's player |

## Known behaviour differences to check

- The matcher's "quality seen" input is empty because Nuvio does not record picture quality per channel.
- `use24HourClock`, guide time shift and similar HBM settings take defaults. Nuvio's own setting for the clock is not read yet.
- Channel ids: Nuvio's channel `id` is used as the stable channel key. Overrides saved by "Fix channel" depend on that id staying
  stable across playlist refreshes. Not verified.

## Parked work (kept on a branch, not in the app)

BannerTV `integration/nuvio-phase1` contains: the stream linker and `LinkerText` moved from the Android-only module into
`shared/commonMain`, a Confirmed/Possible resolver with user overrides (7 tests), a host factory `createSharedSports`, a scale
test (20,102 synthetic channels: index 215 ms, link about 155 microseconds), and an opt-in live probe against today's NHL
scoreboard. They remain useful for BannerTV's own Android TV and Apple apps, and for News and Podcasts if BannerTV `shared` is
used as a dependency. The Nuvio build does not reference them now.

## News and podcasts plan (not started)

BannerTV's news aggregation and podcast resolver are already Kotlin Multiplatform in `shared`, but they need a host: an `EspnApi`,
an HTTP gateway and an RSS decoder (the decoder is Android-only today). The Android podcast screens, store, downloads and audio
service (`mobile/.../PhonePodcasts*.kt`, `PhoneAudioService.kt`, about 580 lines) use Media3 and DownloadManager. Plan:
1. Add BannerTV `shared` as a build dependency again (it resolved and compiled for Android earlier).
2. Write Compose Multiplatform News and Podcasts screens in Nuvio, over `NewsRepository` and `TeamPodcastResolver`, with an RSS decoder per platform.
3. Play episodes through Nuvio's player. Do not port the separate audio service first.
4. iOS: reuse the shared Kotlin; the decoder and any downloads are platform work and unverified.
