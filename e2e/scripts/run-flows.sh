#!/usr/bin/env bash
# Runs Maestro flows on the connected emulator/device, one flow at a time, and records a video of each.
#
#   e2e/scripts/run-flows.sh [flow-dir-or-file ...]    (default: every flow listed in e2e/flows/config.yaml)
#
# Output (OUT_DIR, default e2e/results): junit.xml, <flow>.mp4, and Maestro's screenshots/logs under maestro/.
# Needs: adb and maestro on PATH, the debug APKs already installed (see e2e/README.md).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/e2e/results}"
mkdir -p "$OUT_DIR/maestro"

if [ "$#" -eq 0 ]; then
  # Same set as `maestro test e2e/flows`: the globs in config.yaml (top-level "- glob" lines under "flows:").
  mapfile -t targets < <(awk '/^flows:/{f=1;next} f&&/^ *- /{gsub(/^ *- *"?|"?$/,"");print}' "$ROOT/e2e/flows/config.yaml")
  set -- "${targets[@]}"
  cd "$ROOT/e2e/flows"
fi

shopt -s nullglob
files=()
for target in "$@"; do
  if [ -d "$target" ]; then
    files+=("$target"/*.yaml)
  else
    files+=($target) # unquoted on purpose: config.yaml entries are globs
  fi
done

if [ "${#files[@]}" -eq 0 ]; then
  echo "No flows found." >&2
  exit 1
fi

failed=0
cases=""
for flow in "${files[@]}"; do
  name="$(basename "$flow" .yaml)"
  echo "::group::maestro $flow"
  # screenrecord stops after 3 minutes; each flow is far shorter.
  adb shell screenrecord --time-limit 170 "/sdcard/$name.mp4" &
  rec_pid=$!
  start=$(date +%s)
  if maestro test --test-output-dir "$OUT_DIR/maestro/$name" "$flow"; then
    result="pass"
  else
    result="fail"
    failed=$((failed + 1))
    adb exec-out screencap -p >"$OUT_DIR/$name-failure.png" || true
  fi
  elapsed=$(($(date +%s) - start))
  # SIGINT lets screenrecord finalise the mp4.
  adb shell pkill -2 screenrecord || true
  wait "$rec_pid" 2>/dev/null || true
  sleep 1
  adb pull "/sdcard/$name.mp4" "$OUT_DIR/$name.mp4" >/dev/null 2>&1 || true
  adb shell rm -f "/sdcard/$name.mp4" || true
  echo "::endgroup::"
  echo "$result: $flow (${elapsed}s)"
  if [ "$result" = "fail" ]; then
    cases+="  <testcase classname=\"e2e\" name=\"$name\" time=\"$elapsed\"><failure message=\"Maestro flow failed\"/></testcase>"$'\n'
  else
    cases+="  <testcase classname=\"e2e\" name=\"$name\" time=\"$elapsed\"/>"$'\n'
  fi
done

{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo "<testsuite name=\"maestro\" tests=\"${#files[@]}\" failures=\"$failed\">"
  printf '%s' "$cases"
  echo '</testsuite>'
} >"$OUT_DIR/junit.xml"

echo "Ran ${#files[@]} flow(s), $failed failed."
[ "$failed" -eq 0 ]
