#!/usr/bin/env bash
# Writes a throwaway google-services.json (fake project, fake key) into app/ and kids/ so CI can prove the
# build also works WITH a Firebase config. The files are git-ignored; never use them for a real project.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
write() {
  local dir="$1" package="$2"
  cat > "$root/$dir/google-services.json" <<JSON
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "stayfocused-ci-fake",
    "storage_bucket": "stayfocused-ci-fake.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": { "package_name": "$package" }
      },
      "oauth_client": [],
      "api_key": [{ "current_key": "fake-api-key-for-ci" }],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
JSON
}
write app com.dculus.stayfocused
write kids com.dculus.stayfocused.kids
