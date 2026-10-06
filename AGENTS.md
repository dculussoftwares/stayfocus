# Agent guide: Stay Focused

Stay Focused is a free, open-source (GPL-3.0) Android app blocker, with a companion "Kids" app for parent → child control.

**Before you write code, read:**
1. `docs/IMPLEMENTATION_PLAN.md`: decisions, module map, workflow, definition of done, conventions.
2. `design_handoff_stay_focused_phase1/README.md`: screens, behaviour, design tokens (high fidelity, copy is final).
3. The GitHub issue you're working on. Each story lists its scope, acceptance criteria and dependencies.

**Rules**
- One story = one branch (`m<N>/<issue>-<slug>`) = one PR with `Closes #<issue>`.
- Don't start a story whose "Depends on" issues are still open.
- Stay inside the story's scope; open a new issue for anything else you find.
- Run `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug` before opening a PR.
- The project must build without `google-services.json`.
- Never add permissions, manifest flags or `QUERY_ALL_PACKAGES` unless the story says so (Play policy).
- Infrastructure (GitHub settings, GCP/Firebase) changes **only** through Terraform in `infra/`, applied by GitHub Actions. Never use a console, `gcloud ... create`, or `firebase deploy`. See `docs/INFRASTRUCTURE.md`.
- To change a story or add one, edit `backlog/phase1/` in a PR, not the issue on GitHub.
- The design prototype is HTML for reference only. Rebuild it natively; don't embed it.
