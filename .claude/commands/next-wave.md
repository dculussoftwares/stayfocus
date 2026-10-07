---
description: Run the next unfinished wave of the Phase 1 backlog
argument-hint: "[max-parallel, default 2] [reviewer: coderabbit | qodo]"
---

Run `python3 scripts/backlog/waves.py --status` and read `NEXT_WAVE`.

- `NEXT_WAVE=done` → report "Phase 1 backlog complete" with any open needs-human stories, and stop.
- Otherwise run that wave exactly as the `/wave` command describes in `.claude/commands/wave.md`, with the wave number
  from `NEXT_WAVE` and max parallel workers `$1` (default 2) and reviewer `$2` (default `coderabbit`).
