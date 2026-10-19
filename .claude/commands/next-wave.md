---
description: Run the next unfinished wave of the backlog (Phase 1 or the v5 redesign)
argument-hint: "[max-parallel, default 2] [reviewer: coderabbit | qodo]"
---

Run `python3 scripts/backlog/waves.py --status` and read `NEXT_WAVE`.

- `NEXT_WAVE=done` → report "Backlog complete" with the `WAITING_ON_HUMAN` stories, and stop.
- Otherwise run that wave exactly as the `/wave` command describes in `.claude/commands/wave.md`, with the wave number
  from `NEXT_WAVE` and max parallel workers `$1` (default 2) and reviewer `$2` (default `coderabbit`).
