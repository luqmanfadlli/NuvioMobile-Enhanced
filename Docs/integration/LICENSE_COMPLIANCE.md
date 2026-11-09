# License compliance and provenance

This is an engineering review, not legal advice. Facts below come from files in the checked-out repositories. Anything not
verified is marked.

## Project licenses

| Repository | License found | Evidence |
| --- | --- | --- |
| Nuvio Enhanced (ENH) | GPL-3.0 | `LICENSE` at the repo root |
| homelessbrian mobile (HBM) | GPL-3.0 | `LICENSE` at the repo root |
| homelessbrian TV (HBT) | GPL-3.0 | `LICENSE` at the repo root |
| BannerTV (BTV) | **No license file found** at the root, in `apple/`, or in a docs mention | `ls`, `grep` |
| Upstream NuvioMedia/NuvioMobile | Not checked directly. ENH and HBM are forks and carry GPL-3.0 | n/a |

## Consequences

1. **A combined app that includes ENH or HBM code is a GPL-3.0 combined work.** That includes the Nuvio app once it links
   BannerTV `shared`. The BTV code it links must be distributable under GPL-3.0 terms. A repo with no license is "all rights reserved" by default.
   **Action for the BTV owner:** choose and add a license to BTV (GPL-3.0-or-later is compatible), and record who holds
   copyright on each part. Do not publish a combined binary until this is done.
2. **Provenance of code ported from HBM.** `composeApp/src/*/kotlin/com/nuvio/app/features/sports/**` is copied from
   `homelessbrian/NuvioPlusIPTV-Mobile` `main` @ `233607a9` (`features/livetv/sports`, `features/livetv/ui/sports`, and trimmed helpers
   from `features/livetv/{model,data,sync,parental,record}`), with package names rewritten. HBM is GPL-3.0 and so is this repo, so the
   combination is GPL-3.0. **Open item:** the copied files carry no per-file copyright header, and HBM's own headers were not checked
   (no header found in the files read). Add an attribution header to each ported file before release. `strings_sports.xml` is also copied from HBM.
3. **BTV's own imports.** BTV records that its Apple tree was imported from `StadiaTV-iOS` at `e4946252` with a sanitized
   snapshot (`bannertv/docs/ARCHITECTURE.md`). Bundled reference trees inside it, `apple/Public-ESPN-API-main` and
   `apple/odds-api-main`, have **unverified** licenses.

## Third-party components to review before release (unverified unless stated)

| Component | Where | Note |
| --- | --- | --- |
| MPVKit fork `luqmanfadlli/MPVKit` | ENH git submodule `MPVKit` (not initialised in my checkout, so unreadable) | mpv and FFmpeg builds can be LGPL or GPL depending on flags. Check |
| libmpv, libass, FFmpeg decoders | ENH Android (`mpv-android-lib`, `libass-android/`) | Same question. `libass-android/` is an empty directory in my checkout |
| TorrServer | ENH `vendor/TorrServer` (empty directory in my checkout) | Verify license and that it is not enabled by default |
| quickjs-kt, ksoup | ENH dependencies | Verify |
| Rust native crates | ENH `native/` (`Cargo.toml` declares `AGPL-3.0-only`) | The Android app links these (built through `cargo-ndk`). AGPL has network-use obligations and is a different license from the app's GPL-3.0. Review before release |
| Supabase, Sentry | ENH. Build-time config (`NUVIO_SUPABASE_*`, `SENTRY_DSN`), empty by default | Not hardcoded; users are not connected to fork infrastructure by default. Verify the release workflow secrets |
| Ktor, kotlinx, Compose, Coil, Media3 | both | Apache-2.0, compatible with GPL-3.0 |
| NFL and PGA web-client keys | BTV `scripts/bootstrap-public-sports.py` copies them from the iOS source into ignored build config | Third-party API terms and key sharing need review. Never commit the generated file |
| ESPN, NHL, MLB, FotMob, Sportec, hockeytech endpoints | BTV and HBM | Unofficial or undocumented endpoints. Terms of use unverified. Keep per-provider kill switches |
| TheSportsDB, TVmaze, TMDB | HBM, BTV | Check API terms (TMDB requires attribution) |

## Data and account sync

- HBM Live TV sync defaults to `https://livetv-cloud.nuvioplusiptv.workers.dev` (fork author) and Google Drive OAuth
  (`sync/LiveTvCloudSync.kt`, `sync/LiveTvDriveSync.kt`). HBM says logins are stored encrypted. **Not audited.** Do not enable by default, and do not
  reuse until the encryption, key handling and endpoint ownership are reviewed.
- Nuvio's own Supabase sync is configured at build time and is off without those values.
- Provider and playlist credentials: use platform secure storage. BTV Android uses its own `CredentialStore`. Apple uses Keychain. Nuvio Enhanced keeps playlist blobs in
  platform preferences (`LiveTvStorage`); whether that is encrypted is **unverified** and must be checked before it holds Xtream passwords at scale.

## Checklist before any release

- [ ] BTV has a license file and per-file copyright is recorded.
- [ ] Source offer and notices for GPL components are in the app's About screen.
- [ ] Each bundled native library's license and linking mode checked.
- [ ] No keys or provider credentials in the repository or in generated files that are tracked.
- [ ] Every outbound endpoint listed, with owner and opt-in status.
