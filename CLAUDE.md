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
| `.github/workflows/backlog.yml` | Plan on PR, apply on merge to `main`; board status sync when issues close |
| `.coderabbit.yaml` | CodeRabbit review config (required reviewer on every PR) |
| `scripts/backlog/board.py` | Move a card on the kanban board / list Ready cards |
| `docs/agents/PHASE1_EXECUTION.md` | How to run the backlog (`/wave`, `/next-wave`, `/story`), CodeRabbit rules, the 16-wave order |
| `.claude/commands/`, `.claude/agents/story-worker.md` | The `/wave`, `/next-wave`, `/story` commands and the per-story subagent |

## Commands

Android (available as the M1 stories land):

```bash
scripts/dev/setup-hooks.sh                                           # install pre-commit + pre-push hooks (M1-14)
./gradlew assembleDebug                                              # both apps
./gradlew ciCheck                                                    # everything CI runs (M1-02) = Definition of done
./gradlew spotlessApply                                              # auto-format Kotlin (ktlint + Compose rules)
./gradlew detekt lintAll                                             # static analysis (config/detekt/detekt.yml)
./gradlew unitTests koverXmlReport koverVerify                       # JVM + Robolectric tests, coverage gate (80% logic modules, 60% overall)
./gradlew verifyScreenshots                                          # Roborazzi: compare against committed baselines
./gradlew :core:blocking:testDebugUnitTest --tests '*RuleEvaluator*' # one test class
./gradlew recordRoborazziDebug                                       # update screenshot baselines
./gradlew ciDevicesGroupDebugAndroidTest                             # instrumented tests on managed emulators (M1-16)
maestro test e2e/flows                                               # end-to-end flows on a running emulator (M1-16)
```

Backlog / infra (never apply locally; Actions plans on the PR and applies on merge):

```bash
terraform -chdir=infra/github fmt -recursive && terraform -chdir=infra/github validate
python3 scripts/backlog/waves.py --check          # wave table in sync with backlog/
python3 scripts/backlog/board.py ready             # Ready cards on the kanban board
python3 scripts/backlog/waves.py --status         # progress per wave + NEXT_WAVE
python3 scripts/backlog/waves.py --wave 3          # one wave's stories, issues and state
```

## Working on a story

Use **`/wave <N>`** (a whole wave, parallel workers), **`/next-wave`** or **`/story <ID>`** (one story). One session per wave;
`/clear` between waves. The procedure each worker follows is `.claude/agents/story-worker.md`. In short:
1. Pick a Ready card, then `gh issue view <n>`: read the scope, acceptance criteria and "Depends on".
2. `python3 scripts/backlog/board.py status <n> "In progress"`, then branch `m<N>/<n>-<slug>` from `origin/main`.
3. Implement only the scope and run the Definition-of-done command.
4. Open a PR (`[Mx-yy] <title>`, body `Closes #<n>`) and move the card to *In review*. Work through CodeRabbit's review
   (fix or answer every comment; never `@coderabbitai approve`), then squash-merge once the merge gate is met.
   Don't edit the issue body; Terraform owns it (commit changes to `backlog/phase1/` instead).

## Notes for Claude

- Look up library and API details (Compose, Room, Hilt, Firebase, Terraform providers, Play policy) in current docs
  (Context7 / official docs) before writing code. Versions and APIs change.
- The design copy is final. Take strings from the prototype verbatim.
- This is a **public** repo: never commit secrets, `google-services.json`, keystores, Terraform state,
  `.idea/`, or real user data. Use placeholders in docs.
