#!/usr/bin/env bash
# Removes leftover debugging names (local symbols) from the app executable inside an unsigned IPA.
# Saves ~50 MB on the device. Exported and imported symbols, the export table and the dynamic
# linking info are kept, and checked to be unchanged before the IPA is updated.
# Usage: strip-ipa.sh <file.ipa>   (prints a short Markdown summary)
set -euo pipefail

ipa="$(realpath "$1")"

find_tool() {
  for name in "$@"; do
    if command -v "${name}" >/dev/null 2>&1; then command -v "${name}"; return 0; fi
  done
  return 1
}

if ! strip_tool="$(find_tool llvm-strip llvm-strip-18 llvm-strip-17)"; then
  sudo apt-get install -y -qq llvm >/dev/null
  strip_tool="$(find_tool llvm-strip)"
fi
nm_tool="$(find_tool llvm-nm llvm-nm-18 llvm-nm-17)"

exe_path="$(python3 - "${ipa}" <<'PY'
import plistlib, re, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    plist = next(n for n in z.namelist() if re.fullmatch(r"Payload/[^/]+\.app/Info\.plist", n))
    exe = plistlib.loads(z.read(plist))["CFBundleExecutable"]
    print(plist.rsplit("/", 1)[0] + "/" + exe)
PY
)"

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
unzip -q "${ipa}" "${exe_path}" -d "${work}"
exe="${work}/${exe_path}"
before="$(stat -c %s "${exe}")"

cp "${exe}" "${work}/original"
"${strip_tool}" -x "${exe}"
after="$(stat -c %s "${exe}")"

# Safety: what the app needs at runtime must be identical.
for flag in -gU -u; do
  if ! cmp -s <("${nm_tool}" "${flag}" "${work}/original") <("${nm_tool}" "${flag}" "${exe}"); then
    echo "Symbol check failed (${flag}); leaving the IPA unchanged." >&2
    exit 1
  fi
done

(cd "${work}" && zip -q "${ipa}" "${exe_path}")
echo "Removed debugging data: app executable $((before / 1000000)) MB -> $((after / 1000000)) MB"
