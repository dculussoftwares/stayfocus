#!/usr/bin/env bash
# Fetches Clash Display and Satoshi (ITF Free Font License) into core/ui/src/main/assets/fonts/.
# The files are NOT redistributable, so they are git-ignored. Without them the app uses the bundled
# OFL substitutes (Space Grotesk, Plus Jakarta Sans). See docs/FONTS.md.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
dest="$root/core/ui/src/main/assets/fonts"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$dest"

fetch() { # name url file...  (zip from the Fontshare download endpoint)
  local name="$1" url="$2"; shift 2
  curl -fsSL "$url" -o "$tmp/$name.zip"
  unzip -q -o "$tmp/$name.zip" -d "$tmp/$name"
  for f in "$@"; do
    found="$(find "$tmp/$name" -type f -name "$f" | head -n1)"
    [ -n "$found" ] || { echo "missing $f in $name archive" >&2; exit 1; }
    cp "$found" "$dest/$f"
  done
}

fetch clash "https://api.fontshare.com/v2/fonts/download/clash-display" ClashDisplay-Semibold.otf
fetch satoshi "https://api.fontshare.com/v2/fonts/download/satoshi" \
  Satoshi-Regular.otf Satoshi-Medium.otf Satoshi-Bold.otf Satoshi-Black.otf
echo "Fonts installed in $dest"
