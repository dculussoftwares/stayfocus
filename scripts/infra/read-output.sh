#!/usr/bin/env bash
# Print one Terraform output (JSON) straight from the state in GCS, without terraform init.
# Usage: read-output.sh <module> <output>. Needs TF_STATE_BUCKET (infra/prod.env) and a gcloud login (the plan
# identity is enough). Prints nothing and exits 1 when the state or the output does not exist yet.
set -euo pipefail
module="$1"
output="$2"
state="$(gcloud storage cat "gs://${TF_STATE_BUCKET:?}/${module}/default.tfstate" 2>/dev/null)" || exit 1
printf '%s' "$state" | jq -ec --arg o "$output" '.outputs[$o].value // error("missing")' 2>/dev/null
