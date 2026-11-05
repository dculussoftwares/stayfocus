#!/usr/bin/env bash
# Writes the real google-services.json into app/ and kids/ from the infra/gcp Terraform state in GCS (output
# google_services_json). Needs a gcloud login (the read-only plan identity is enough) and TF_STATE_BUCKET from
# infra/prod.env. The files are git-ignored; never commit them.
# REQUIRED=1 fails when the output is missing (release builds); otherwise it warns and builds without Firebase.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
if ! json="$("$root/scripts/infra/read-output.sh" gcp google_services_json)"; then
  if [ "${REQUIRED:-0}" = "1" ]; then
    echo "::error title=No Firebase config::The infra/gcp output google_services_json is not available."
    exit 1
  fi
  echo "::warning title=No Firebase config yet::The infra/gcp output google_services_json is not available. Building without it."
  exit 0
fi
umask 077
for app in app kids; do
  printf '%s' "$json" | jq -r --arg a "$app" '.[$a]' > "$root/$app/google-services.json"
  echo "Wrote $app/google-services.json"
done
