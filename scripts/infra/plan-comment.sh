#!/usr/bin/env bash
# Post (or update) one PR comment per Terraform module with the plan summary and the full plan.
# Usage: plan-comment.sh <module> <plan.txt>. Env: GH_TOKEN, GITHUB_REPOSITORY, PR_NUMBER.
set -euo pipefail
module="$1"
plan="$2"
heading="### Terraform plan: infra/${module}"
{
  echo "$heading"
  echo
  grep -E '^(Plan:|No changes\.|Error)' "$plan" || echo "Plan did not complete; see the job log."
  echo
  echo "<details><summary>Full plan</summary>"
  echo
  echo '```'
  tail -c 60000 "$plan"
  echo '```'
  echo "</details>"
} > comment.md
id=$(gh api "repos/${GITHUB_REPOSITORY}/issues/${PR_NUMBER}/comments" --paginate \
  --jq "[.[] | select(.user.type == \"Bot\" and (.body | startswith(\"${heading}\")))] | last | .id // empty" | tail -n 1)
if [ -n "$id" ]; then
  gh api -X PATCH "repos/${GITHUB_REPOSITORY}/issues/comments/${id}" -F body=@comment.md > /dev/null
else
  gh pr comment "$PR_NUMBER" --repo "$GITHUB_REPOSITORY" --body-file comment.md
fi
