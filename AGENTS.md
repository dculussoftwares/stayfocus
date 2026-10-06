# Agent guide: Stay Focused

Stay Focused is a free, open-source (GPL-3.0) Android app blocker, with a companion "Kids" app for parent → child control.

**Before you write code, read:**
1. `docs/IMPLEMENTATION_PLAN.md`: decisions, module map, workflow, definition of done, conventions.
2. `design_handoff_stay_focused_phase1/README.md`: screens, behaviour, design tokens (high fidelity, copy is final).
3. The GitHub issue you're working on. Each story lists its scope, acceptance criteria and dependencies.

**Rules**
- Commit **directly to `main`** (no feature branches or PRs). The commit that finishes a story ends with `Closes #<issue>`.
- Track work on the **kanban board** (https://github.com/orgs/dculussoftwares/projects/11): pick from *Ready*, move to *In progress* (`scripts/backlog/board.py`); *Done* happens when the issue closes.
- Prompts and the parallel execution order are in `docs/agents/PHASE1_EXECUTION.md`.
- Don't start a story whose "Depends on" issues are still open.
- Stay inside the story's scope; open a new issue for anything else you find.
- Run `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug` before every push; `main` must stay green. `git pull --rebase` before pushing; never force-push.
- The project must build without `google-services.json`.
- Never add permissions, manifest flags or `QUERY_ALL_PACKAGES` unless the story says so (Play policy).
- Infrastructure (GitHub settings, GCP/Firebase) changes **only** through Terraform in `infra/`, applied by GitHub Actions. Never use a console, `gcloud ... create`, or `firebase deploy`. See `docs/INFRASTRUCTURE.md`.
- To change a story or add one, edit `backlog/phase1/` and commit it, not the issue on GitHub.
- The design prototype is HTML for reference only. Rebuild it natively; don't embed it.
