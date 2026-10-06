#!/usr/bin/env bash
# terraform fmt + validate + tflint for every Terraform root that has a changed file under infra/.
# Needs `terraform` on PATH. tflint is downloaded once into ~/.cache/stayfocus-hooks if it is not installed.
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd)"
tflint_v=0.64.0

command -v terraform >/dev/null || { echo "terraform is not installed (brew install terraform)" >&2; exit 1; }

tflint_bin="$(command -v tflint || true)"
if [ -z "$tflint_bin" ]; then
  cache="${XDG_CACHE_HOME:-$HOME/.cache}/stayfocus-hooks"
  tflint_bin="$cache/tflint-$tflint_v"
  if [ ! -x "$tflint_bin" ]; then
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"
    case "$arch" in x86_64) arch=amd64 ;; aarch64) arch=arm64 ;; esac
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    mkdir -p "$cache"
    curl -fsSL "https://github.com/terraform-linters/tflint/releases/download/v$tflint_v/tflint_${os}_${arch}.zip" -o "$tmp/t.zip"
    unzip -q "$tmp/t.zip" -d "$tmp"
    mv "$tmp/tflint" "$tflint_bin"
  fi
fi

# Terraform roots = directories that contain a changed *.tf / *.tfvars / *.hcl file.
dirs=""
for f in "$@"; do
  case "$f" in
    *.tf | *.tfvars | *.hcl) dirs="$dirs
$(dirname "$f")" ;;
  esac
done
dirs="$(printf '%s\n' "$dirs" | sort -u)"
status=0
for d in $dirs; do
  [ -d "$root/$d" ] || continue
  echo "== $d"
  terraform -chdir="$root/$d" fmt -check -diff || status=1
  terraform -chdir="$root/$d" init -backend=false -input=false -no-color >/dev/null || status=1
  terraform -chdir="$root/$d" validate -no-color || status=1
  "$tflint_bin" --chdir="$root/$d" || status=1
done
exit "$status"
