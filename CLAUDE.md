# CLAUDE.md

Guidance for Claude Code in this repository. The shared rules for every coding agent are in `AGENTS.md`:

@AGENTS.md

## Repository status

Phase 1 is planned, not built yet. The Android code starts with story **M1-01** (Gradle skeleton). Until then, the repo holds:

| Path | What |
|---|---|
| `design_handoff_stay_focused_phase1/` | Design handoff: `README.md` (screens, tokens, behaviour) + an HTML prototype (open in Chrome; its `<script>` block holds the dial maths, AI prompt and copy) |
| `docs/IMPLEMENTATION_PLAN.md` | Decisions, module map, workflow, definition of done, data model, backend/Functions policy |
| `docs/INFRASTRUCTURE.md` | Terraform + GitHub Actions only; HCP state; bootstrap |
| `backlog/phase1/` | Epics and stories (source of truth for the GitHub issues) |
| `infra/github/` | Terraform that turns `backlog/` into labels, milestones and issues |
| `scripts/backlog/sync_project.py` | Post-apply sync: sub-issues, "blocked by" links, Projects v2 board |
| `.github/workflows/backlog.yml` | Plan on PR, apply on merge |

## Commands

Android (available from M1-01 / M1-02):

```bash
./gradlew assembleDebug                                              # both apps
./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug  # Definition of done / CI
./gradlew :core:blocking:testDebugUnitTest --tests '*RuleEvaluator*' # one test class
./gradlew recordRoborazziDebug                                       # update screenshot baselines
```

Backlog / infra (never apply locally; Actions applies on merge):

```bash
terraform -chdir=infra/github fmt -recursive && terraform -chdir=infra/github validate
python3 -m py_compile scripts/backlog/sync_project.py
```

## Working on a story

1. `gh issue view <n>`: read the scope, acceptance criteria and "Depends on". Don't start if a dependency is still open.
2. Branch `m<N>/<issue>-<slug>`, implement only the scope, run the Definition-of-done command.
3. PR titled `[M<N>] <title> (#<issue>)` with `Closes #<issue>` and the acceptance criteria ticked in the PR body.
   Don't edit the issue body; Terraform owns it (change `backlog/phase1/` in a PR instead).

## Notes for Claude

- Look up library and API details (Compose, Room, Hilt, Firebase, Terraform providers, Play policy) in current docs
  (Context7 / official docs) before writing code. Versions and APIs change.
- The design copy is final. Take strings from the prototype verbatim.
- This is a **public** repo: never commit secrets, `google-services.json`, keystores, Terraform state,
  `.idea/`, or real user data. Use placeholders in docs.
