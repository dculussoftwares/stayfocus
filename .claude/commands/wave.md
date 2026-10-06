---
description: Run one wave of the Phase 1 backlog — parallel story-worker subagents, each in its own worktree
argument-hint: <wave-number> [max-parallel, default 2]
---

Run **wave $1** of the Stay Focused Phase 1 backlog. Max parallel workers: **$2** (if empty, use 2).

You are the coordinator. Keep this session small: don't implement stories yourself, don't read source files, and
don't paste large logs. Workers do the work in their own contexts and return a short report.

1. **Sync and list the wave**
   - `git fetch origin && git switch main && git pull --ff-only origin main`
   - `python3 scripts/backlog/waves.py --status` → if wave $1 is later than `NEXT_WAVE`, stop: the earlier wave must
     finish first (say which stories are still open).
   - `python3 scripts/backlog/waves.py --wave $1` → one line per story: id, issue, state, size, needs-human, deps, title.
     Skip stories already CLOSED. If none are left, report "wave $1 already done" and stop.

2. **Run the workers** — rolling, at most the max-parallel number at a time.
   For each open story start the `story-worker` subagent with:
   - `subagent_type: "story-worker"`, `isolation: "worktree"`, `run_in_background: true`,
   - `name: "<ID>"`, `description: "Story <ID>"`,
   - prompt: `Implement story <ID> (issue #<N>).` plus, for needs-human stories, `This story is labelled needs-human.`
   Start the larger stories (size L) first. When a worker finishes, start the next story in the wave.
   Don't poll or sleep while waiting; you're notified when a worker completes.

3. **Handle each report**
   - DONE / ALREADY-DONE → record it.
   - BLOCKED → record the blocker; don't retry blindly. Retry once only if the cause was transient (CI flake, merge
     conflict after another story merged).
   - NEEDS-HUMAN → record exactly what the person must do.

4. **Check and summarise** when every worker has reported:
   - `python3 scripts/backlog/waves.py --status` (the wave's stories should be closed, except needs-human ones).
   - Output a compact table: story | issue | PR | result | CodeRabbit outcome | follow-ups / manual checks.
   - List anything that needs me (blockers, needs-human actions, decisions).
   - Finish with: "Next: `/clear`, then `/wave <next>`" (or `/next-wave`), or "Phase 1 complete" if `NEXT_WAVE=done`.

Stop and ask me instead of continuing when a worker reports a decision only a human can make, or when two workers in a
row are BLOCKED for the same reason (likely a shared problem such as missing credentials or a broken `main`).
