# Upstream sync strategy

Lineage: `NuvioMedia/NuvioMobile` (`cmp-rewrite`) -> `NuvioMobile-Enhanced` (`enhanced`, tracks upstream releases) -> this integration.
HBM is a sibling fork of the same upstream. HBT is based on NuvioTV and is not a merge source.

## Rules that keep merges small

1. **Additive by default.** Integration code goes in new files under `composeApp/src/*/kotlin/com/nuvio/app/features/banner/`
   and in `Docs/integration/`. No upstream file is renamed or reformatted.
2. **Upstream files get only small hook edits**, each listed below with the reason. Keep each hook to a few lines that call into `features/banner/`.
3. **Sports and matching rules never live in this repo.** They live in BannerTV `shared`, so upstream Nuvio merges cannot conflict with them.
4. **Ported HBM code** goes in its own package (`features/banner/ported/...`), not into `features/livetv`, so a later HBM sync can be diffed against
   the original file and Nuvio's `features/livetv` keeps merging cleanly.
5. Merge upstream with `git merge`, not rebase, on the integration branch, and record the merged upstream tag here.

## Hook edits to upstream files (current)

| File | Change | Why |
| --- | --- | --- |
| `.gitignore` | `Docs` -> `Docs/*` plus `!Docs/integration/` | Upstream ignores the whole `Docs` folder (some files are force-tracked). Without this the integration docs cannot be committed |
| `composeApp/.../navigation/Routes.kt` | Three routes: `SportsRoute`, `SportsGameRoute`, `SportsLeagueRoute` | Sports hub navigation |
| `composeApp/.../AppNavigationSupport.kt` | Three serializer registrations | Same |
| `composeApp/.../MainAppContent.kt` | Imports, `sportsActions`, deep-link `LaunchedEffect`, three `entry<>` blocks, `onSportsClick` | Mount the hub, play channels through the existing Live TV player path |
| `composeApp/.../AppShellComponents.kt` | `onSportsClick` field and one argument | Pass the Sports button action to the Live TV screen |
| `composeApp/.../features/livetv/LiveTvScreen.kt` | Optional `onSportsClick` parameter, a header icon, one import | Entry point |
| `composeApp/.../core/deeplink/AppUrlBridge.kt` | One line in `handleAppUrl` | Route `nuvio://livetv/...` links to the hub |
| `composeApp/src/androidMain/.../NuvioApplication.kt` | One line: `SportsAndroid.initialize(this)` | Give the hub's Android helpers a context |
| `androidApp/src/main/AndroidManifest.xml` | `SportsAlarmReceiver`, `SportsLiveService`, and permissions `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` | Reminders, live-score service, re-arming after reboot. `USE_EXACT_ALARM` was deliberately not added (Play restricts it to alarm and calendar apps) |
| `composeApp/src/commonMain/composeResources/values/strings_sports.xml` | New file (65 strings) | Sports hub strings (English only) |

Ported code lives only in new files: `features/sports/**` and the `strings_sports.xml` resource file.

## Known conflict areas

- HBM wired Live TV into about 20 shared files (player, home, search, details, streams, watch progress, settings, deep links, `MainActivity`, manifest).
  Cherry-picking any of those hooks will conflict with ENH's own Live TV hooks.
- BannerTV is no longer a build dependency of the Nuvio app (only needed again for News and Podcasts).
- Toolchain: ENH Kotlin 2.4.10 / Gradle 9.8.2 / AGP 9.4.0. HBM AGP 9.2.1. BannerTV Kotlin 2.4.21 / Gradle 9.4.1.

## Merge log

| Date | Source | Result |
| --- | --- | --- |
| 2026-11-07 | none yet | Branch cut from `enhanced` @ `bb361fa4` |
| 2026-11-07 | homelessbrian `main` @ `233607a9` (Sports hub) | Ported by copy with package rewrite, not a git merge. Provenance of each file: `features/sports` mirrors `features/livetv/sports` and `features/livetv/ui/sports` at that commit |
