#!/usr/bin/env bash
# One-time local setup for NuvioMobile-Enhanced (zenkaia0iw fork).
# Usage: bash scripts/setup-fork.sh [target-dir]
set -euo pipefail

ORIGIN="https://github.com/zenkaia0iw/NuvioMobile-Enhanced.git"
UPSTREAM="https://github.com/NuvioMedia/NuvioMobile.git"
DIR="${1:-NuvioMobile-Enhanced}"

if [ ! -d "$DIR/.git" ]; then
  git clone "$ORIGIN" "$DIR"
fi
cd "$DIR"

# Remotes
git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "$UPSTREAM"
git fetch --all --prune

# Track the release line
git checkout enhanced 2>/dev/null || git checkout -b enhanced origin/enhanced
git pull --ff-only origin enhanced

# Submodules (MPVKit)
git submodule update --init --recursive

# Bring in upstream as a sync branch, never straight onto enhanced
BRANCH="sync/upstream-$(date -u +%Y%m%d)"
git fetch upstream cmp-rewrite
if git merge-base --is-ancestor upstream/cmp-rewrite HEAD; then
  echo "Already up to date with upstream/cmp-rewrite."
else
  git checkout -b "$BRANCH"
  if git merge --no-edit upstream/cmp-rewrite; then
    echo "Clean merge on $BRANCH. Build, then: git push -u origin $BRANCH and open a PR into enhanced."
  else
    echo "Conflicts on $BRANCH. Resolve them per docs/FORK_WORKFLOW.md, then commit."
  fi
fi

echo
echo "Setup complete. Remotes:"
git remote -v
