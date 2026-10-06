# Agent guide: Stay Focused

Stay Focused is a free, open-source (GPL-3.0) Android app blocker, with a companion "Kids" app for parent → child control.

**Before you write code, read:**
1. `docs/IMPLEMENTATION_PLAN.md`: decisions, module map, workflow, definition of done, conventions.
2. `design_handoff_stay_focused_phase1/README.md`: screens, behaviour, design tokens (high fidelity, copy is final).
3. The GitHub issue you're working on. Each story lists its scope, acceptance criteria and dependencies.

**Rules**
- One story = one branch (`m<N>/<issue>-<slug>`) = one PR with `Closes #<issue>`, reviewed by **CodeRabbit** (`.coderabbit.yaml`).
  Fix or answer every CodeRabbit comment; merge only when the merge gate is met (CI green, `CodeRabbit` check done, 0 unresolved threads, CodeRabbit approved or no new actionable comments on HEAD; see `docs/agents/PHASE1_EXECUTION.md`). Never self-approve or dismiss its review.
- Track work on the **kanban board** (https://github.com/orgs/dculussoftwares/projects/11): pick from *Ready*, move to *In progress* when you start and *In review* when the PR is open (`scripts/backlog/board.py`); *Done* happens when the PR merges.
- Run work with `/wave <N>`, `/next-wave` or `/story <ID>` (Claude Code). The per-story procedure is `.claude/agents/story-worker.md`; the parallel wave order is in `docs/agents/PHASE1_EXECUTION.md`.
- Don't start a story whose "Depends on" issues are still open.
- Stay inside the story's scope; open a new issue for anything else you find.
- Install the git hooks once per clone/worktree (`scripts/dev/setup-hooks.sh`; `pre-commit run --all-files` must pass). Run `./gradlew ciCheck` (from M1-02; before that, `./gradlew assembleDebug`) before every push. Never push to `main` directly or force-push it.
- The project must build without `google-services.json`.
- Never add permissions, manifest flags or `QUERY_ALL_PACKAGES` unless the story says so (Play policy).
- Infrastructure (GitHub settings, GCP/Firebase) changes **only** through Terraform in `infra/`, applied by GitHub Actions. Never use a console, `gcloud ... create`, or `firebase deploy`. See `docs/INFRASTRUCTURE.md`.
- To change a story or add one, edit `backlog/phase1/` and commit it, not the issue on GitHub.
- The design prototype is HTML for reference only. Rebuild it natively; don't embed it.
