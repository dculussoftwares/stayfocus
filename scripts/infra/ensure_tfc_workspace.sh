#!/usr/bin/env bash
# Ensure an HCP Terraform workspace exists with Local execution mode (state only; runs happen in GitHub Actions).
# Idempotent. Used by the Terraform workflows before `terraform init`.
#
# Env: TF_API_TOKEN (team token), TF_CLOUD_ORGANIZATION, TF_WORKSPACE
set -euo pipefail

: "${TF_API_TOKEN:?TF_API_TOKEN is not set}"
: "${TF_CLOUD_ORGANIZATION:?TF_CLOUD_ORGANIZATION is not set}"
: "${TF_WORKSPACE:?TF_WORKSPACE is not set}"

api="https://app.terraform.io/api/v2"
auth=(-H "Authorization: Bearer ${TF_API_TOKEN}" -H "Content-Type: application/vnd.api+json")

status=$(curl -sS -o /dev/null -w '%{http_code}' "${auth[@]}" \
  "${api}/organizations/${TF_CLOUD_ORGANIZATION}/workspaces/${TF_WORKSPACE}")

case "$status" in
  200)
    echo "HCP workspace ${TF_CLOUD_ORGANIZATION}/${TF_WORKSPACE} exists"
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
