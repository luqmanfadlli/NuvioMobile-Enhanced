# Test results

Host: macOS, JDK 26, Android SDK platforms 36 and 36.1 (AGP downloaded compileSdk 37.1 and the NDK on demand), **no full Xcode**.
Dates are 2026-11-06/07. Commands run from each repo root. Logs are kept next to the repos in `BannerTV-Integration/`.

## Baselines (before any integration change)

| Target | Command | Result |
| --- | --- | --- |
| BannerTV `shared` JVM | `./gradlew :shared:jvmTest -PpublicBuild=true` | **PASS**: 236 tests, 37 classes, 0 failures |
| Nuvio Enhanced Android main sources | `./gradlew :composeApp:testAndroidHostTest -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` | Main sources **compile**. Needs an ignored `local.properties` with `sdk.dir` |
| Nuvio Enhanced Android host tests | same command | **FAIL to compile, before any change of mine.** `compileAndroidHostTest` reports 26 errors in upstream test files that no longer match the production code (for example `FloatingNavigationBarGestureTest`, `AndroidDownloadTransferTest`, `NextEpisodeCardTest`, `NavigationSavedStateConfigurationTest`). The tests were **not** disabled or edited. No Nuvio test result exists for this reason |
| Nuvio iOS (`iosSimulatorArm64Test`) | not run | **Untested**: full Xcode is not installed |
| BannerTV Apple targets, Android TV app, Android phone app | not run | **Untested** |

## After Phase 1 changes

| Target | Command | Result |
| --- | --- | --- |
| BannerTV `shared` common metadata | `./gradlew :shared:compileCommonMainKotlinMetadata` | PASS |
| BannerTV `shared` + `core` unit tests, after moving the linker | `./gradlew :core:testDebugUnitTest :shared:jvmTest -PpublicBuild=true` | **PASS**. `:core` 457 tests, 0 failures; `:shared` includes the moved `StreamLinkerTest` (15, 0 failures) |
| New resolver tests | `./gradlew :shared:jvmTest --tests '*EventBroadcastsTest*'` | **PASS**: 7 tests, 0 failures |
| Nuvio Android main with BannerTV wired in | `./gradlew :composeApp:compileAndroidMain -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` | **PASS** (3m13s). This also shows the Kotlin/Ktor/coroutines version skew resolves for Android |
| Nuvio debug APK | `./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` | **PASS** (after installing Rust 1.99.1, the four Android Rust targets and `cargo-ndk` 4.1.2; the first attempt failed because `cargo` was missing). Output: `androidApp/build/outputs/apk/full/debug/` |
| On-device run of the vertical slice | Android emulator | **Not verified: the emulator cannot run on this Mac.** A fresh AVD (`Banner_Test`, `android-37.1` arm64, 16 KB page-size image) never boots: the emulator logs `hvf is not enabled on this aarch64 host` and `qemu_mprotect__osdep: mprotect failed: Permission denied`, also when launched outside the tool sandbox. The pre-existing `Pixel_10` AVD folder was missing. cmdline-tools were installed to create the new one. The APK is built but has never been launched |
| Live probe: real events to resolved channels | `BANNER_LIVE=1 ./gradlew :shared:jvmTest --tests '*LiveEventResolutionProbeTest*' -i -PpublicBuild=true` | **PASS** on 2026-11-07. `createSharedSports` (the factory Nuvio uses) returned 3 real NHL games from the live scoreboard (for example `hockey/nhl|401892659` Columbus Blue Jackets at Buffalo Sabres). Against a guide **fixture** listing those real team names, ESPN resolved as **Confirmed** with both HD/FHD mirrors and an unrelated weather channel matched nothing. The playlist and guide are fixtures. Nuvio's XMLTV import path and UI are not exercised by this test |

### Scale check (synthetic data)

`./gradlew :shared:jvmTest --tests '*StreamLinkerScaleTest*'`: 20,102 streams (3 quality variants per feed), 30,151 guide programmes.
Index build **215 ms**. Linking and resolving one event **about 155 microseconds** once warm (200-run average). The test asserts only loose
bounds (15 s build, 50 ms per event) and prints the numbers. This is generated data on a JVM, not a real 20k-channel provider and not an Android device.
The existing Android `LineupPerfTest` in BannerTV `core` remains the real-data reference.

### What the 7 resolver tests cover

1. A guide listing naming both teams is Confirmed, and HD/FHD mirrors become one broadcast with two sources.
2. A channel whose *name* matches the event is never Confirmed.
3. Event-channel kickoff times are read in the event's zone on both sides of the US fall-back date (EDT and EST).
4. A listing for another day does not confirm this event.
5. Rejections persist across rebuilding the linker (guide refresh).
6. A user pin outranks heuristics even when the linker finds nothing.
7. Same teams on different provider ids keep separate event identities.

### Not yet tested

Playlist import (M3U, Xtream), XMLTV accuracy, persistence migrations, interrupted streams and failover, track switching, PiP, 20,000-channel performance, existing Nuvio movie and TV flows, iOS, tvOS, Android TV. None of these has a result, so none is claimed.


## After the Sports hub port (2026-11-07)

| Target | Command | Result |
| --- | --- | --- |
| Nuvio Android main sources with the ported hub | `./gradlew :composeApp:compileAndroidMain -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` | **PASS** (after fixing 473 initial errors from missing helper types, recording code and Drive code) |
| Nuvio debug APK | `./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` | **PASS**, 7m45s. `apkanalyzer` shows `SportsAlarmReceiver` in the manifest and the `features.sports` classes in the dex. `SportsLiveService` and four permissions were missing from the first build and were added afterwards; the manifest merge passes (`:androidApp:processFullDebugManifest`), the APK was not rebuilt after that edit |
| On-device behaviour of the hub | not possible | **Not verified**: the emulator cannot boot on this host |
| Ported-code unit tests | not ported | **None exist** for `features/sports` |
| iOS compile of the ported code | not run | **Untested** (no full Xcode) |
