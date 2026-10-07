#!/usr/bin/env bash
# Ensure an HCP Terraform workspace exists with Local execution mode (state only; runs happen in GitHub Actions).
# Idempotent. Used by the Terraform workflows before `terraform init`.
#
# Env: TF_API_TOKEN (team token), TF_CLOUD_ORGANIZATION, TF_WORKSPACE
#      ENSURE_MODE=check  fail (instead of fixing) when an existing workspace is not in Local execution mode;
#                         used by pull-request plan jobs, which must not change shared settings before merge.
set -euo pipefail

: "${TF_API_TOKEN:?TF_API_TOKEN is not set}"
: "${TF_CLOUD_ORGANIZATION:?TF_CLOUD_ORGANIZATION is not set}"
: "${TF_WORKSPACE:?TF_WORKSPACE is not set}"

api="https://app.terraform.io/api/v2"
auth=(-H "Authorization: Bearer ${TF_API_TOKEN}" -H "Content-Type: application/vnd.api+json")

body=$(mktemp)
trap 'rm -f "$body"' EXIT
status=$(curl -sS -o "$body" -w '%{http_code}' "${auth[@]}" \
  "${api}/organizations/${TF_CLOUD_ORGANIZATION}/workspaces/${TF_WORKSPACE}")

case "$status" in
  200)
    echo "HCP workspace ${TF_CLOUD_ORGANIZATION}/${TF_WORKSPACE} exists"
    # Runs happen in GitHub Actions (cloud credentials live there), so the workspace must use Local execution mode.
    mode=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["attributes"]["execution-mode"])' "$body")
    ws_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["id"])' "$body")
    if [ "$mode" != "local" ] && [ "${ENSURE_MODE:-fix}" = "check" ]; then
      echo "::error::HCP workspace ${TF_WORKSPACE} uses '${mode}' execution mode; it must be 'local'. It is fixed by the next apply run (or set it in HCP)."
      exit 1
    elif [ "$mode" != "local" ]; then
      echo "Switching ${TF_WORKSPACE} from '${mode}' to local execution mode"
      curl -sS --fail-with-body "${auth[@]}" -X PATCH "${api}/workspaces/${ws_id}" \
        -d '{"data":{"type":"workspaces","attributes":{"execution-mode":"local"}}}' > /dev/null
    fi
    ;;
  404)
    echo "Creating HCP workspace ${TF_CLOUD_ORGANIZATION}/${TF_WORKSPACE} (execution mode: local)"
    curl -sS --fail-with-body "${auth[@]}" -X POST "${api}/organizations/${TF_CLOUD_ORGANIZATION}/workspaces" \
      -d "{\"data\":{\"type\":\"workspaces\",\"attributes\":{\"name\":\"${TF_WORKSPACE}\",\"execution-mode\":\"local\",\"description\":\"Managed from GitHub Actions (${GITHUB_REPOSITORY:-dculussoftwares/stayfocus})\"}}}" \
      > /dev/null
    ;;
  *)
    echo "::error::HCP API returned HTTP ${status} for workspace lookup (check TF_API_TOKEN and TF_CLOUD_ORGANIZATION)"
    exit 1
    ;;
esac
