#!/usr/bin/env bash
# Writes the real dev google-services.json into app/ and kids/ from the Terraform output of infra/firebase
# (data source google_firebase_android_app_config). Needs TF_CLOUD_ORGANIZATION, TF_WORKSPACE and an HCP token
# (TF_API_TOKEN via setup-terraform). The files are git-ignored; never commit them.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root/infra/firebase"
terraform init -input=false >/dev/null
json="$(terraform output -json google_services_json)"
for app in app kids; do
  printf '%s' "$json" | python3 -c 'import base64,json,sys; config=base64.b64decode(json.load(sys.stdin)[sys.argv[1]], validate=True); json.loads(config); sys.stdout.buffer.write(config)' "$app" > "$root/$app/google-services.json"
  echo "Wrote $app/google-services.json"
done
