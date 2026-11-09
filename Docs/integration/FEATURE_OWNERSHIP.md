# Feature ownership (revised 2026-11-07)

"Owner" is the code that is the single source of truth in the combined app. Reasons and history: `ARCHITECTURE.md`, `SOURCE_AUDIT.md`.

| Capability | Owner | Platforms | Status |
| --- | --- | --- | --- |
| Movies, shows, addons, library, trackers, profiles | Nuvio Enhanced | Android, iOS | Unchanged |
| Playback (VOD and live) | Nuvio Enhanced player | Android, iOS | Unchanged |
| Playlists (M3U, Xtream, Stalker), XMLTV, channels, favourites, guide UI | Nuvio Enhanced `features/livetv` | Android, iOS | Unchanged |
| Sports feeds, standings, league catalog, event identity | HBM port: `features/sports/SportsFeeds`, `SportsRepository`, `SportsStandings`, `SportsCatalog`, `SportsModels` | Android (iOS compiles in theory, unverified) | Ported, compiles, never run |
| Sports hub screens | HBM port: `features/sports/ui/*` | same | Ported, compiles, never run |
| My Sports rows, follows, hide scores, league networks | HBM port | same | Ported, compiles, never run |
| Sports-to-channel matching | HBM port `SportsMatcher` over `compat/NuvioIptvBridge` | same | Ported. Never run against real playlist and guide |
| Manual channel correction ("Fix channel") | HBM port | same | Ported. Persistence of picks not verified |
| Game reminders, live-score notifications | HBM port + `compat/SportsAlarmPlatform` | Android real. **iOS no-op** | Compiles. Never run |
| Recording | Not included | n/a | Left out (needs its own design) |
| Catch-up from sports page | Not included | n/a | Nuvio has no catch-up |
| Cloud/Drive sync of sports data | Not included | n/a | Excluded for security review |
| News | BannerTV `shared` `NewsRepository` | planned | Not started |
| Podcasts | BannerTV `shared` `TeamPodcastResolver` + new Compose screens | planned | Not started |
| BannerTV Game Centre, per-sport native providers, fantasy | Not included in the Nuvio app | n/a | Dropped by decision |
| Android TV, Fire TV, tvOS, CarPlay | BannerTV apps (unchanged, separate) | n/a | Out of this app |
