# Agent guide: Vesper

Vesper is a free, open-source (GPL-3.0) Android app blocker, with a companion "Kids" app for parent → child control.

**Before you write code, read:**
1. `docs/IMPLEMENTATION_PLAN.md`: decisions, module map, workflow, definition of done, conventions.
2. `docs/design/V5_SPEC.md` and `design/Stay Focused v5.dc.html`: screens, behaviour, design tokens (high fidelity, copy is final).
   `design_handoff_stay_focused_phase1/` is the superseded v2 handoff, kept as history only.
3. The GitHub issue, if the work has one. A backlog story is **not** required: work can start directly from a request.

**Rules**
- One change = one branch = one PR (add `Closes #<issue>` when there is an issue), reviewed by **CodeRabbit** (`.coderabbit.yaml`).
  Fix or answer every CodeRabbit comment; merge only when the merge gate is met (CI green, 0 unresolved threads, every Qodo/Greptile/CodeRabbit finding fixed or answered; CodeRabbit is advisory and never blocks a merge; see `docs/agents/PHASE1_EXECUTION.md`). Never self-approve. If CodeRabbit is rate limited, **Qodo** is the fallback reviewer (`/agentic_review`, `.pr_agent.toml`); see `docs/agents/PHASE1_EXECUTION.md`.
- Optional, only when working an existing backlog story: track it on the **kanban board** (https://github.com/orgs/dculussoftwares/projects/11) with `scripts/backlog/board.py`, run it with `/wave <N>`, `/next-wave` or `/story <ID>` (`.claude/agents/story-worker.md`, `docs/agents/PHASE1_EXECUTION.md`), and don't start it while its "Depends on" issues are open. New work doesn't need a story or issue first.
- Install the git hooks once per clone/worktree (`scripts/dev/setup-hooks.sh`; `pre-commit run --all-files` must pass). Run `./gradlew ciCheck` (from M1-02; before that, `./gradlew assembleDebug`) before every push. Never push to `main` directly or force-push it.
- The project must build without `google-services.json`.
- Add permissions or manifest flags only when the change needs them, and list each one in the PR description (Play policy). Never add `QUERY_ALL_PACKAGES`.
- Infrastructure (GitHub settings, GCP/Firebase) changes **only** through Terraform in `infra/`, applied by GitHub Actions. Never use a console, `gcloud ... create`, or `firebase deploy`. See `docs/INFRASTRUCTURE.md`.
- If you do change an existing story, edit `backlog/phase1/` and commit it, not the issue on GitHub (Terraform owns issue bodies).
- The design prototype is HTML for reference only. Rebuild it natively; don't embed it.
