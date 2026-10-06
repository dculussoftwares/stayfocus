---
description: Implement one Phase 1 story end to end (e.g. /story M1-07) — PR, CodeRabbit review, merge
argument-hint: <story-id e.g. M1-07>
---

Implement story **$1** of the Stay Focused Phase 1 backlog.

Start the `story-worker` subagent with `subagent_type: "story-worker"`, `isolation: "worktree"`, `name: "$1"`,
`description: "Story $1"` and the prompt `Implement story $1.` Wait for its report (you're notified when it finishes),
then show me the report and, if it's DONE, run `python3 scripts/backlog/waves.py --status` and say which wave is next.

Use this to run a single story, or to retry one that was BLOCKED in a `/wave` run once the blocker is fixed.
