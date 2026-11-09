# Implementation progress

Last updated 2026-11-07. **Nothing is committed or pushed.** Branches: `nuvio-enhanced` `integration/bannertv-phase0-1`, BannerTV clone `integration/nuvio-phase1` (parked, see below).

## Direction (confirmed by the owner 2026-11-07)

One app on the Nuvio Enhanced shell. Nuvio Enhanced keeps its own IPTV and entertainment logic. The Sports hub, sports data and
matching come from homelessbrian mobile. News and podcasts come from BannerTV mobile. Game Centre depth and fantasy from BannerTV are
not included. Android and iOS for News and Podcasts (shared Compose).

## Done

- Audit, with baselines: `SOURCE_AUDIT.md`, `TEST_RESULTS.md`.
- **Sports hub ported into Nuvio Enhanced** (`features/sports/**`, 55 Kotlin files under `features/sports`) from homelessbrian `main` @ `233607a9`:
  hub screens, sports feeds and repository, standings, league catalog, follows, hide scores, league networks, matcher, reminders and live-score notification.
- A bridge (`features/sports/compat/NuvioIptvBridge.kt`) lets the ported code read Nuvio's own channels and guide instead of homelessbrian's.
- Wiring: Sports icon in the Live TV header, three routes, deep links, channel playback through Nuvio's existing path, manifest entries, init hook, 65 strings.
- Removed from the port on purpose: recording, Google Drive/cloud sync, parental controls, catch-up button, per-game playback failover (see `ARCHITECTURE.md`).
- Verified: `:composeApp:compileAndroidMain` passes and `:androidApp:assembleFullDebug` builds an APK (170 MB) containing the sports classes, the alarm receiver and (after a fix) the live-score service.

## Not done / not verified

- **The app has never been launched.** The Android emulator cannot boot on this Mac (no hardware virtualization, see `TEST_RESULTS.md`). So none of the following has been seen working:
  the Live TV Sports icon, the hub tabs, real events loading, a channel resolving for a game, following a team and having it persist, a reminder firing.
- Matching against a real playlist and guide: not tried. It only sees the 3-hour-back, 48-hour-ahead guide Nuvio keeps in memory.
- iOS: the Kotlin common code is shared, but the iOS actuals (files, key-value, alarms) are written and **never compiled** (no full Xcode). Reminders are a no-op on iOS.
- No automated tests exist for the ported code. HBM has `SportsMatcherTest`, `SportsCatalogTest` and others in its `commonTest`; they were not ported. Nuvio's own Android test sources do not compile (26 stale upstream test errors), so a port of those tests would not run until that is addressed.
- News and podcasts: not started. Plan in `ARCHITECTURE.md`.
- Strings are English only.

## Next steps

1. Run the APK on a real Android device (or fix the emulator host) and walk through: Live TV, Sports icon, All Events, follow a team, open a game, import a playlist and XMLTV, see which channels match, tap one to play.
2. Port HBM's sports tests (`SportsMatcherTest`, `SportsCatalogTest`) and decide how to run them given the broken Nuvio test sources.
3. Add an attribution header to every ported file (`LICENSE_COMPLIANCE.md`).
4. News and Podcasts (Android and iOS) over BannerTV `shared`.
5. Make the iOS side real: compile with Xcode, implement reminders with local notifications.
6. Decide on recording (port HBM's, or leave out) and catch-up.

## Parked: BannerTV `integration/nuvio-phase1`

Stream linker and `LinkerText` moved to `shared/commonMain`; Confirmed/Possible resolver with overrides (7 tests); host factory `createSharedSports`;
config-cache fix for `generateProviderDefaults`; scale test (20,102 synthetic channels: index 215 ms, 155 microseconds per event); opt-in live probe against
today's NHL scoreboard. All pass. Uncommitted. Not used by the Nuvio build now. `:core:testDebugUnitTest` (457) and `:shared:jvmTest` pass with these changes.

## Limitations

- BannerTV `main` and `codex/apple-foundation-nhl` have diverged (Cinema module versus StreamLinker/foundation work).
- `Docs/integration/` is used instead of `docs/integration/` because macOS ignores case and git tracks `Docs/`.
