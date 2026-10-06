#!/usr/bin/env bash
# Installs the pre-commit and pre-push hooks in this clone/worktree. Run once per clone or worktree.
set -euo pipefail
cd "$(dirname "$0")/../.."

if ! command -v pre-commit >/dev/null; then
  if command -v pipx >/dev/null; then
    pipx install pre-commit
  elif command -v brew >/dev/null; then
    brew install pre-commit
  elif command -v pip3 >/dev/null; then
    pip3 install --user pre-commit
    PATH="$(python3 -m site --user-base)/bin:$PATH"
    export PATH
  else
    echo "Install pre-commit first (pipx install pre-commit, or brew install pre-commit)." >&2
    exit 1
  fi
fi

pre-commit install --hook-type pre-commit --hook-type pre-push
echo "Hooks installed. Run them on everything with: pre-commit run --all-files"
echo "Tools used by some hooks: terraform (infra/) and a JDK (ktlint)."
