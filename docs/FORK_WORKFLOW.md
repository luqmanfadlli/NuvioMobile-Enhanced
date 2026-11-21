# Fork workflow: NuvioMobile-Enhanced

This fork tracks upstream `NuvioMedia/NuvioMobile` (branch `cmp-rewrite`) and adds features on top.

## Remotes

```bash
git remote add upstream https://github.com/NuvioMedia/NuvioMobile.git
git remote -v   # origin = zenkaia0iw/NuvioMobile-Enhanced, upstream = NuvioMedia
```

## Branches

| Branch | Purpose |
|---|---|
| `enhanced` | Release line. Always buildable. Upstream merges and feature merges land here. |
| `feature/<name>` | One feature or fix. Branch from `enhanced`, PR back into `enhanced`. |
| `sync/upstream-YYYYMMDD` | Created by the daily upstream-sync workflow. Review, then merge into `enhanced`. |
| `hotfix/<name>` | Urgent fix to the latest release. Branch from the release tag, PR into `enhanced`. |

Do not commit directly to `enhanced`.

## Daily routine

1. Let `upstream-sync.yml` run, or trigger it with **Actions → Upstream sync → Run workflow**.
2. If it opens a PR, review the diff, build Android (`./gradlew :androidApp:assembleFullDebug`) and iOS, then merge with a **merge commit** (not squash), so upstream history stays traceable.
3. If it opens an issue about conflicts, follow the section below.

## Manual upstream merge (same thing, locally)

```bash
git fetch upstream
git checkout enhanced && git pull origin enhanced
git checkout -b sync/upstream-$(date +%Y%m%d)
git merge upstream/cmp-rewrite
# resolve conflicts, run builds
git push -u origin HEAD
```

## Adding a feature

```bash
git checkout enhanced && git pull
git checkout -b feature/my-feature
# commit in small, focused commits using conventional prefixes: feat(player): ..., fix(home): ...
git push -u origin feature/my-feature
```

Open a PR into `enhanced` using `.github/PULL_REQUEST_TEMPLATE.md`. Each feature should also get a row in the README's "What Enhanced adds" section.

## Keeping feature branches current

Merge `enhanced` into your feature branch rather than rebasing once it's shared:

```bash
git checkout feature/my-feature
git merge enhanced
```

## Resolving upstream conflicts

Rule of thumb: upstream wins on shared plumbing, the fork wins on the features it adds. Before resolving, read both sides and the upstream PR that changed the file.

1. `git status` lists conflicted files.
2. For each file, decide: keep upstream's structure and re-apply the fork's change on top, or keep the fork's version if upstream removed the code the feature depends on.
3. Build both platforms before pushing.
4. Note non-obvious decisions in the merge commit message.

Features upstream has since adopted are dropped from the README list (the README already says this). Remove them from code only when upstream's version fully covers them.

## Releases

Release automation lives in `.github/workflows/mobile-release.yml` (and `ios-test-build.yml` for iOS builds).

- Stable: tag `vX.Y.Z-enhanced.N` on `enhanced`, where `X.Y.Z` is the upstream version being tracked and `N` counts fork releases on that base.
- Pre-release (beta): tag `vX.Y.Z-enhanced.N-beta.M` and publish as a pre-release, which feeds `store-beta.json`.

Steps:

1. Confirm `enhanced` is green and the sync PR (if any) is merged.
2. Bump versions in `gradle.properties` / iOS project as the release workflow expects.
3. Update the README "What Enhanced adds" table for any new feature.
4. `git tag vX.Y.Z-enhanced.N && git push origin vX.Y.Z-enhanced.N`
5. Confirm the release workflow attached the APK and IPA, and that `store.json` / `store-beta.json` point at the new build.

## Conventions

- Commits: Conventional Commits (`feat`, `fix`, `chore`, `docs`, `refactor`), scope in parentheses.
- PRs: one feature or sync per PR; link the upstream PR number when cherry-picking.
- Never commit secrets. Run the GitHub secret-scanning check before pushing config changes.
- `MPVKit` is a git submodule (`luqmanfadlli/MPVKit`, branch `nuvio-enhanced`). After cloning run `git submodule update --init --recursive`.
