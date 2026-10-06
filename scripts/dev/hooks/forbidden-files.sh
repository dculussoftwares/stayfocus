#!/usr/bin/env bash
# Blocks files that must never be committed to this public repo.
set -euo pipefail
status=0
for f in "$@"; do
  base="$(basename "$f")"
  case "$base" in
    .env.example | .env.sample | .env.template) continue ;;
    google-services.json | *.jks | *.keystore | *.tfstate | *.tfstate.* | .env | .env.*)
      echo "Forbidden file: $f (secrets, signing keys and Terraform state are never committed)" >&2
      status=1
      ;;
  esac
done
exit "$status"
