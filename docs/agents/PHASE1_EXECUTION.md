# Phase 1 execution: prompts and order

How to get coding agents to build all of Phase 1 from the GitHub issues. Work is tracked on the **GitHub Project kanban board**
[org project #11](https://github.com/orgs/dculussoftwares/projects/11) (columns: Backlog → Ready → In progress → Done). Agents **commit directly to `main`**;
the commit that finishes a story says `Closes #<issue>`, which closes the issue and moves its card to Done.
Stories in the same wave run **in parallel**.

- **Prompt A: Orchestrator.** Runs the whole of Phase 1 (or a range of waves) and starts parallel workers.
- **Prompt B: Story worker.** Builds one story end to end and closes its issue. Works with any coding agent.
- **Prompt C: One wave.** Runs every story of one wave in parallel, without the orchestrator loop.
- **Execution order:** the generated table at the end.

---

## Before you start (once)

1. The backlog issues and the board must exist. The `backlog` workflow creates them on every push to `main` that touches
   `backlog/` (complete the bootstrap in `docs/INFRASTRUCTURE.md` first). Check with:
   `gh issue list --repo dculussoftwares/stayfocus --label type:story --state all --limit 200 | wc -l` → 74,
   and `python3 scripts/backlog/board.py ready` → lists `[M1-01]`.
2. The agent's `gh` account needs **write** access to the repo and the `project` scope
   (`gh auth refresh -h github.com -s project,workflow`).
3. **Kanban view:** the board is created by the workflow, but GitHub has no API for views. Open the project once and
   choose *New view → Board* (grouped by Status), then save it as the default view.
4. Infra stories (`infra` label) apply through GitHub Actions on push to `main` and need the secrets from
   `docs/INFRASTRUCTURE.md` (and from M1-13 on, the GCP bootstrap). To review Terraform changes before they apply,
   require reviewers on the `backlog`, `dev` and `prod` GitHub environments.

### The kanban flow

| Column | Who moves it there |
|---|---|
| Backlog | Board sync: one or more dependencies are still open |
| Ready | Board sync: every dependency is closed. **Pick work from here** |
| In progress | The agent, when it starts: `python3 scripts/backlog/board.py status <N> "In progress"` |
| Done | Automatic: the `Closes #N` commit reaches `main` → issue closed → board sync |

Closing an issue also re-runs the board sync, which moves newly unblocked stories from Backlog to Ready.
(*In review* stays unused while work goes straight to `main`.)

---

## Prompt A: Orchestrator (all of Phase 1)

Paste into Claude Code from the repo root. Change the wave range or concurrency as needed.

````text
You are the orchestrator for Stay Focused Phase 1 in the repo dculussoftwares/stayfocus.
Goal: implement every story in docs/agents/PHASE1_EXECUTION.md, wave by wave, from wave 1 to wave 16.
All work is committed directly to main. Each story ends with its issue closed by a "Closes #N" commit and
its card in Done on the kanban board (https://github.com/orgs/dculussoftwares/projects/11).

Read first: AGENTS.md, docs/IMPLEMENTATION_PLAN.md, docs/INFRASTRUCTURE.md, and the "Execution order" table
in docs/agents/PHASE1_EXECUTION.md.

Loop, for each wave W in order:
1. Refresh: `git pull --rebase origin main`, then `python3 scripts/backlog/board.py ready`.
   The open stories of wave W should all be in Ready. A wave-W story still in Backlog means an earlier wave
   isn't finished: finish that first. Never start a story with an open dependency.
2. Start one worker per Ready story of the wave, at most 4 at a time, each in its own git worktree
   (Agent tool, isolation "worktree"). Give each worker Prompt B from docs/agents/PHASE1_EXECUTION.md with
   STORY_ID filled in. Stories marked 🧑 (needs-human) also get Prompt B; the worker prepares what it can and
   hands over.
3. Workers push straight to main. Each pulls with rebase right before pushing, and retries if the push is
   rejected because main moved. Never force-push main.
4. When all workers in the wave report back, check each issue is CLOSED and its card is in Done. Re-run a
   worker at most once for a failed story; after that, stop and report it.
5. Watch CI on main (`gh run list --branch main --limit 10`). If main is red, fix forward before starting
   the next wave.
6. After each wave, post a short summary: commits, closed issues, anything blocked, follow-ups, and
   manual checks still pending (from docs/qa/pending-manual-checks.md).

Stop and ask me when:
- a story is blocked on credentials, billing, a Play Console action or another human-only step;
- a dependency can't be met, or CI on main stays red after a retry;
- a story would need a decision not covered by its issue or docs (product copy, a new permission,
  scope changes).
Never invent infrastructure outside Terraform, never commit secrets (public repo), and never edit the
issue bodies (Terraform owns them). If a story needs changing, commit a change to backlog/phase1/.
Start with wave 1.
````

---

## Prompt B: Story worker (one story, end to end)

Replace `{{STORY_ID}}` (e.g. `M1-01`). This works on its own with any coding agent that has `git`, `gh` and the Android/Terraform tooling.

````text
You are implementing story {{STORY_ID}} of Stay Focused (repo dculussoftwares/stayfocus, public, GPL-3.0).
Finish it completely: code + tests, committed directly to main, CI green on main, the issue closed, and the
card in Done on the kanban board (https://github.com/orgs/dculussoftwares/projects/11).

1. Find the issue
   gh issue list --repo dculussoftwares/stayfocus --state all --limit 200 --json number,title,state \
     --jq '.[] | select(.title|startswith("[{{STORY_ID}}]"))'
   If it's CLOSED, report "already done" and stop. Call its number N.

2. Check it's ready
   Read it: gh issue view N --repo dculussoftwares/stayfocus
   Every story under "Depends on" must be CLOSED (look them up the same way), and the card should be in
   Ready (python3 scripts/backlog/board.py ready). If a dependency is open, stop and report which one.

3. Claim it on the board
   gh issue edit N --add-assignee @me
   python3 scripts/backlog/board.py status N "In progress"
   gh issue comment N --body "🤖 Started {{STORY_ID}}."

4. Read before coding
   AGENTS.md, CLAUDE.md, docs/IMPLEMENTATION_PLAN.md (§4 workflow and Definition of done, §5 conventions,
   §8 backend policy), docs/INFRASTRUCTURE.md for `infra` stories, and design_handoff_stay_focused_phase1/
   (README + the prototype's <script> for exact copy and maths). Look up current library and API docs
   (Context7 / official docs) instead of relying on memory.

5. Sync
   git switch main && git pull --rebase origin main
   (If you're in a separate worktree, work on a local branch there and rebase it onto origin/main before
   pushing it to main: git push origin HEAD:main.)

6. Implement exactly the issue's Scope
   - Meet every acceptance criterion. Write tests first for pure logic.
   - Stay in scope. Note anything else as a follow-up; don't build it.
   - Copy is final: take strings from the prototype verbatim, into strings.xml.
   - Infrastructure only through Terraform in infra/ (applied by Actions on push to main).
   - Never commit secrets, google-services.json, keystores, Terraform state or personal data (public repo).

7. Verify locally before every push; main must stay green
   - Android: ./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug (once M1-02 exists;
     before that, ./gradlew assembleDebug).
   - Terraform: terraform fmt -check -recursive && terraform validate in the changed root.
   - Functions/rules: the npm test / emulator tests in firebase/.
   - Backlog changes: python3 scripts/backlog/waves.py --check.
   - UI: previews + screenshot tests; run on an emulator where an acceptance criterion needs a device.
   If an acceptance criterion needs a physical device, a second phone, a screen recording or a human, do
   everything else, then add an entry to docs/qa/pending-manual-checks.md (story, check, how to do it).

8. Commit to main
   Use small commits while you work if helpful. The final commit message must be:
     [{{STORY_ID}}] <issue title without the id>

     <what changed, 2-5 lines>
     Tested: <commands run and results>
     Pending manual checks: <list or "none">

     Closes #N
   (plus your co-author trailer if your tool uses one)
   git pull --rebase origin main && <re-run the step-7 checks if anything came in> && git push origin HEAD:main
   If the push is rejected because main moved, repeat the pull/check/push. Never force-push main.

9. Get CI green on main
   gh run list --repo dculussoftwares/stayfocus --branch main --commit $(git rev-parse HEAD) and
   gh run watch <run-id>. If it fails, fix forward with a new commit at once (at most 3 rounds); if it's
   still red, revert your commits (git revert), push, reopen the issue with the failure details, and stop.
   For `infra` commits, read the Terraform apply log. Anything unexpected (deletes, replacements) → stop
   and ask. A failure caused only by missing credentials → comment "blocked on credentials: <which>" on
   the issue and stop.

10. Confirm it's closed and Done
    gh issue view N --json state --jq .state  → must be CLOSED. If not:
    gh issue close N --comment "Done in <commit url>."
    If the card isn't in Done after the board sync: python3 scripts/backlog/board.py status N "Done".
    Add a closing comment on the issue that ticks each acceptance criterion (copy them from the issue)
    and lists pending manual checks and follow-ups.

11. Report
    Story id, issue, commit(s), a 3-line summary, pending manual checks, follow-ups, and any decision you
    made that wasn't in the issue.

Stories labelled needs-human: do the repo part (docs, checklists, drafts, scripts) and commit it to main
WITHOUT "Closes #N". Leave the issue OPEN and In progress, with a comment listing exactly what a person
must do.
````

---

## Prompt C: One wave in parallel

Use this to drive one wave at a time, or to run waves in several terminals or tools.

````text
Run wave {{W}} of docs/agents/PHASE1_EXECUTION.md for dculussoftwares/stayfocus (commits go directly to main;
progress is tracked on the kanban board (https://github.com/orgs/dculussoftwares/projects/11)).
1. From the "Execution order" table, take the stories in wave {{W}}. Check they're all in Ready
   (python3 scripts/backlog/board.py ready). If any is still in Backlog, stop and list its open dependencies.
2. Start one worker per story (at most 4 in parallel, each in its own git worktree) with Prompt B and
   that STORY_ID.
3. Workers pull with rebase before every push to main and never force-push.
4. Report: each story → commits, issue closed?, card in Done?, pending manual checks, follow-ups, blockers.
````

**Without an orchestrator** (for example several terminals, or other agent tools): open one session per Ready card of
the current wave, paste Prompt B into each, and move to the next wave when every card in the wave is in Done.

---

## Execution order

<!-- waves:start -->
<!-- Generated by scripts/backlog/waves.py. Don't edit by hand. -->

**74 stories in 16 waves.** Run the waves in order; the stories inside a wave can run in parallel.
🧑 = `needs-human`: an agent prepares what it can, then hands over to a person.

| Wave | Parallel stories | Count |
|---|---|---|
| 1 | `M1-01` | 1 |
| 2 | `M1-02`, `M1-03`, `M1-04`, `M2-02`, `M3-02`, `M6-02` | 6 |
| 3 | `M1-05`, `M1-06`, `M1-07`, `M1-08`, `M1-10`, `M1-12`, `M1-13`, `M2-01`, `M3-01`, `M9-01`, `M10-03` | 11 |
| 4 | `M1-09`, `M1-11`, `M2-03`, `M3-03`, `M6-01`, `M6-07`, `M9-02`, 🧑 `M10-07` | 8 |
| 5 | `M2-04`, `M3-05`, `M4-01`, `M5-01`, `M5-03`, `M7-01`, `M10-01` | 7 |
| 6 | `M2-05`, `M2-06`, `M2-07`, `M2-08`, `M4-02`, `M4-05`, `M5-02`, `M5-06`, `M7-02`, `M7-03`, `M9-03` | 11 |
| 7 | `M2-09`, `M3-04`, `M4-03`, `M5-04`, `M6-03`, `M6-04`, `M9-04` | 7 |
| 8 | `M4-04`, `M5-05`, `M6-05`, `M7-06`, `M9-05` | 5 |
| 9 | `M6-06`, `M7-04` | 2 |
| 10 | `M7-05`, `M7-08` | 2 |
| 11 | `M7-07`, `M8-02` | 2 |
| 12 | `M7-09`, `M7-10`, `M8-01` | 3 |
| 13 | `M8-03`, `M8-04` | 2 |
| 14 | `M8-05`, `M10-02`, 🧑 `M10-05` | 3 |
| 15 | `M8-06`, 🧑 `M10-06` | 2 |
| 16 | `M10-04`, `M10-08` | 2 |

<details><summary>Every story with its dependencies</summary>

| Wave | Story | Size | Title | Depends on |
|---|---|---|---|---|
| 1 | `M1-01` | L | Gradle multi-module project skeleton | — |
| 2 | `M1-02` | M | CI pipeline and code-quality gates | M1-01 |
| 2 | `M1-03` | M | Domain model and formatters (:core:model) | M1-01 |
| 2 | `M1-04` | M | Design tokens, fonts and StayFocusedTheme | M1-01 |
| 2 | `M2-02` | M | Foreground-app detection AccessibilityService | M1-01 |
| 2 | `M3-02` | M | Installed apps catalog (no QUERY_ALL_PACKAGES) | M1-01 |
| 2 | `M6-02` | M | App: optional Firebase integration | M1-01 |
| 3 | `M1-05` | L | Core components I: buttons, panels, toggles, chips, tabs, fields | M1-04 |
| 3 | `M1-06` | M | Core components II: bottom sheet, toast, screen transitions | M1-04 |
| 3 | `M1-07` | M | RotaryDial component | M1-03, M1-04 |
| 3 | `M1-08` | M | Screen-time gauge and LED bar chart components | M1-03, M1-04 |
| 3 | `M1-10` | M | Room database schema v1 | M1-03 |
| 3 | `M1-12` | S | GitHub repository settings as code | M1-02 |
| 3 | `M1-13` | L | GCP/Firebase Terraform foundation (dev environment) | M1-02 |
| 3 | `M2-01` | L | Rule evaluator (pure Kotlin) | M1-03 |
| 3 | `M3-01` | L | UsageStats data source | M1-03 |
| 3 | `M9-01` | M | AI result contract and normalisation | M1-03 |
| 3 | `M10-03` | M | Release build and signing pipeline | M1-02 |
| 4 | `M1-09` | L | App navigation shell: 4 tabs, sub-screens, sheets | M1-05, M1-06 |
| 4 | `M1-11` | M | DataStore settings and repository layer | M1-10 |
| 4 | `M2-03` | M | Block screen | M1-05, M1-06 |
| 4 | `M3-03` | M | Usage repository: 7-day cache and averages | M3-01, M1-10 |
| 4 | `M6-01` | L | Terraform: Firebase project, Android apps and Auth providers | M1-13 |
| 4 | `M6-07` | M | Web: privacy policy and account-deletion page | M1-12 |
| 4 | `M9-02` | M | On-device fallback parser | M9-01 |
| 4 | `M10-07` 🧑 | M | Closed testing: 12 testers × 14 days | M10-03 |
| 5 | `M2-04` | L | Blocking engine orchestrator | M2-01, M2-02, M2-03, M1-11 |
| 5 | `M3-05` | M | Insights screen | M3-03, M1-08, M1-09 |
| 5 | `M4-01` | M | Block tab: target picker, list, templates | M1-09, M1-11, M3-02 |
| 5 | `M5-01` | S | Welcome screen | M1-09 |
| 5 | `M5-03` | M | Permissions repository and settings intents | M1-11, M2-02 |
| 5 | `M7-01` | M | Terraform: Firestore, rules release and App Check enforcement | M6-01, M6-02 |
| 5 | `M10-01` | S | Privacy policy and Data safety answers | M6-07 |
| 6 | `M2-05` | M | Block now and Take a break (engine + Home sheet) | M2-04, M1-07, M1-09 |
| 6 | `M2-06` | M | Daily and hourly limits | M2-04, M3-01 |
| 6 | `M2-07` | M | "Use, then rest" cycles | M2-04 |
| 6 | `M2-08` | S | Schedules ("Block during hours") | M2-04 |
| 6 | `M4-02` | M | Wizard host, step 1 (type) and step 2 (apps) | M4-01, M1-07 |
| 6 | `M4-05` | S | All apps tab with quick lock | M4-01, M3-03 |
| 6 | `M5-02` | M | Sign-in / create-account screen (UI + validation) | M5-01 |
| 6 | `M5-06` | S | Battery-optimisation guidance for OEMs | M5-03 |
| 6 | `M7-02` | L | Cloud Functions scaffold, security baseline and Terraform deploy | M7-01 |
| 6 | `M7-03` | L | Firestore data model and security rules + emulator tests | M7-01 |
| 6 | `M9-03` | M | Terraform: Firebase AI Logic, App Check for AI, API key restrictions | M7-01 |
| 7 | `M2-09` | M | Engine robustness: reboot, process death, edge cases | M2-05, M2-06, M2-07, M2-08 |
| 7 | `M3-04` | L | Home dashboard | M3-03, M1-08, M1-09, M2-05 |
| 7 | `M4-03` | L | Wizard step 3: rule editors, summary and save | M4-02 |
| 7 | `M5-04` | M | System check screen and onboarding completion | M5-03, M5-02, M1-06 |
| 7 | `M6-03` | M | Email and password auth + password reset | M6-02, M5-02 |
| 7 | `M6-04` | M | Google sign-in with Credential Manager | M6-02, M5-02 |
| 7 | `M9-04` | M | Gemini client via Firebase AI Logic | M9-01, M9-02, M9-03, M6-02 |
| 8 | `M4-04` | M | Edit, toggle and delete blocks | M4-03 |
| 8 | `M5-05` | S | Accessibility disclosure sheet | M5-04 |
| 8 | `M6-05` | M | Account screen | M6-03, M5-03, M5-06 |
| 8 | `M7-06` | M | Parent: Link a child's phone + scanner | M1-09, M6-03 |
| 8 | `M9-05` | M | AI describe UI and AI DRAFT review | M9-04, M4-03 |
| 9 | `M6-06` | M | Account deletion (in-app, deleteAccount function) | M6-05, M7-02 |
| 9 | `M7-04` | M | Kids app shell, anonymous auth and kids permission setup | M6-02, M5-04, M5-05, M5-06 |
| 10 | `M7-05` | M | Kids: show QR with one-time token | M7-03, M7-04 |
| 10 | `M7-08` | M | Kids: foreground service, persistent notification, IsMonitoringTool | M7-04 |
| 11 | `M7-07` | L | Pairing handshake: claim, 6-digit code, consent | M7-02, M7-03, M7-05, M7-06 |
| 11 | `M8-02` | M | Child status reporter | M7-08 |
| 12 | `M7-09` | M | Kids home and unlink (unlinkDevice function) | M7-07, M7-08 |
| 12 | `M7-10` | M | Devices tab and linked-devices repository | M7-07 |
| 12 | `M8-01` | L | Command pipeline: Firestore commands + child listener | M7-07, M7-08, M7-03 |
| 13 | `M8-03` | L | Child enforcement of parent blocks, locks and focus | M8-01, M2-09 |
| 13 | `M8-04` | L | Parent: remote device screen | M8-01, M8-02, M7-10 |
| 14 | `M8-05` | L | Unlock requests + notifyParent push function | M8-03, M8-04, M7-02 |
| 14 | `M10-02` | S | Manifest and declarations audit (automated guard) | M7-08, M8-03 |
| 14 | `M10-05` 🧑 | S | Store listings for both apps | M3-04, M8-04 |
| 15 | `M8-06` | M | Tamper alerts | M8-02, M8-04, M8-05 |
| 15 | `M10-06` 🧑 | M | Play Console declarations and demo videos | M10-02, M10-05 |
| 16 | `M10-04` | M | Terraform: prod environment, monitoring and budgets | M9-03, M8-06 |
| 16 | `M10-08` | M | Device QA matrix on Android 16 and OEMs | M8-06, M9-05 |

</details>
<!-- waves:end -->

### Notes on the order

- **Critical path** (longest dependency chain, 16 stories): M1-01 → M1-04 → M1-05 → M1-09 → M5-01 → M5-02 → M5-04 → M5-05
  → M7-04 → M7-05 → M7-07 → M8-01 → M8-03 → M8-05 → M8-06 → M10-04. Linking and remote control (M7, M8) set the overall
  length, so keep those lanes staffed.
- **Start M10-07 (closed-test recruiting) early** (wave 4). Play needs 12 testers opted in for 14 consecutive days
  before production.
- **Bootstrap gates:** M1-13 needs the GCP billing account and a one-time bootstrap credential. M6-01 and every later
  `infra` story need the Firebase dev project from it.
- **Parallel commits to main:** stories in the same wave touch different modules by design. The likely hot spots are
  `settings.gradle.kts`, `gradle/libs.versions.toml` and the `:app` navigation graph, so keep those edits small and
  rebase often.
- When the backlog changes (new or edited stories), regenerate this table with `python3 scripts/backlog/waves.py`
  (`--check` fails if it's stale).
