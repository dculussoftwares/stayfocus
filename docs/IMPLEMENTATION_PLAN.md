# Stay Focused: Phase 1 implementation plan

This is the engineering plan for Phase 1 (v1.0). Product scope, screens and design tokens live in
[`design_handoff_stay_focused_phase1/README.md`](../design_handoff_stay_focused_phase1/README.md). Read that first.

The work is tracked as **10 epics (one per milestone) with stories as sub-issues** on the
**GitHub Project kanban board** ([org project #11](https://github.com/orgs/dculussoftwares/projects/11)) for `dculussoftwares/stayfocus`. Every story is meant to be
picked up and finished by one coding agent or developer in **one pull request, reviewed by CodeRabbit**. This document is the shared
contract all stories refer to. Agent prompts and the parallel execution order are in
[`docs/agents/PHASE1_EXECUTION.md`](agents/PHASE1_EXECUTION.md).

---

## 1. Decisions (agreed)

| Topic | Decision |
|---|---|
| Platform | Android only, Kotlin + Jetpack Compose, Material 3 as base, fully custom-themed (dark only) |
| SDK levels | `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` (Coil 3.6 and Compose 1.12 require it; `targetSdk` stays 36) |
| DI | Hilt (KSP) |
| Persistence | Room (schema exported to `core/data/schemas`) + Preferences DataStore |
| Async | Coroutines + Flow; ViewModels expose a single `StateFlow<UiState>` |
| Navigation | Navigation Compose with **type-safe `@Serializable` routes** |
| Build | Gradle version catalog (`gradle/libs.versions.toml`) + convention plugins in `build-logic/` |
| Packages | `com.dculus.stayfocused` (main app), `com.dculus.stayfocused.kids` (Kids app) |
| Two apps | `:app` = self-control + parent. `:kids` = child companion (carries `IsMonitoringTool`, FGS `specialUse`, persistent notification) |
| Accounts | **Optional.** "Continue without an account" on sign-in. Self-blocking and AI work with no account; an account is needed only to link a child's phone. The child phone has no account (Firebase Anonymous Auth under the hood) |
| Backend | **Firebase-first**: Auth, Firestore + security rules, App Check, FCM, AI Logic (Gemini). Client SDKs talk to Firebase directly; **Cloud Functions (TypeScript, 2nd gen, Blaze plan) only where security or clean architecture needs them** (5 functions, see §9). Lives in `/firebase` |
| Infrastructure | **Only Terraform, applied by GitHub Actions** (state in HCP Terraform), for GitHub (backlog, repo settings, Pages) and GCP/Firebase. Built phase by phase: each milestone's `infra` stories add only what it needs. See `docs/INFRASTRUCTURE.md` |
| Backlog | Lives in `backlog/phase1/` as Markdown + front matter and is turned into issues by `infra/github`. Edit the files, not the issues |
| Open source | GPL-3.0. Forks bring their own `google-services.json`; the app **must build and run without it** (Firebase features disabled) |
| Fonts | Clash Display + Satoshi (Fontshare) bundled pending a license check for OSS redistribution; Geist Mono (OFL) |
| "Use, then rest" | The *use* window counts **foreground time only**. When used up, the app locks for *rest* minutes; the next open after that starts a new window |
| Tests | JUnit4 + Turbine + MockK unit tests (JVM + Robolectric) with Kover coverage; Roborazzi screenshot tests; Compose UI / instrumented tests on Gradle Managed Devices (API 26 + 36); **Maestro** end-to-end flows; Firestore emulator tests for rules/functions; Jest for Functions |
| Quality gates | **Pre-commit hooks** (gitleaks, ktlint, terraform fmt/tflint, actionlint, YAML) + pre-push (detekt, unit tests); **CI** (`ci-pass`): Spotless/ktlint, detekt, Android Lint, unit tests + coverage, screenshot tests, instrumented tests, build; **security**: CodeQL, dependency review, Dependabot, gitleaks, Trivy (IaC), zizmor (workflows); **CodeRabbit** review on every PR |

---

## 2. Module map

```
:app                       Stay Focused (self + parent)                → feature:*, core:*
:kids                      Stay Focused Kids (child companion)         → core:blocking, core:usage, core:data, core:sync, core:ui
:core:model                Pure Kotlin domain models (Block, Rule, Break, Device, AppInfo, ...)    → (nothing)
:core:ui                   Theme, type, tokens, components (dial, gauge, LED bars, toggle, sheet)  → core:model
:core:data                 Room DB, DAOs, DataStore, repositories                                   → core:model
:core:usage                UsageStatsManager queries, installed-apps catalog, aggregation           → core:model, core:data
:core:blocking             AccessibilityService, rule evaluator, engine, block screen                → core:model, core:data, core:usage, core:ui
:core:sync                 Firebase Auth, Firestore, FCM, AI Logic wrappers                          → core:model, core:data
:feature:onboarding        Welcome, sign-in, permissions, disclosure
:feature:home              Home dashboard, Take a break sheet
:feature:block             Block tab, wizard, templates, All apps, AI describe
:feature:devices           Devices tab, link/scan/confirm, remote device screen
:feature:insights          Insights
:feature:account           Account, permission health, delete account
/firebase                  firestore.rules, Cloud Functions (TS), emulator tests (deployed by Terraform, never the Firebase CLI)
/web                       Privacy policy + account deletion page (GitHub Pages)
/infra/github              Terraform: labels, milestones, backlog issues, repo settings
/infra/bootstrap           Terraform: GCP projects, Workload Identity Federation, deploy SA
/infra/firebase            Terraform: Firebase/GCP resources, grown milestone by milestone
/backlog/phase1            Epics and stories (source of truth for the GitHub issues)
```

`:core:model` is an addition to the handoff's layout. It keeps domain types free of Android so the rule
evaluator and AI parser can be unit-tested on the JVM.

Feature modules **must not depend on each other**. Cross-feature navigation goes through route objects
defined in `:app` (or a small `:core:navigation` if needed). `:feature:*` modules depend on `:core:*` only.

---

## 3. Milestones and dependency graph

| # | Epic | Depends on | Parallelisable inside? |
|---|---|---|---|
| M1 | Foundations | — | Yes, once M1-01 (skeleton) lands |
| M2 | Blocking engine | M1 (M2-05 limits also needs M3-01) | Evaluator ‖ detection ‖ block screen |
| M3 | Usage and Insights | M1 | Data source ‖ apps catalog, then screens |
| M4 | Block wizard + dial | M1, M3-02 (apps catalog) | Yes |
| M5 | Onboarding + permissions | M1 | Yes |
| M6 | Accounts | M1, M5 (sign-in screen shell) | Firebase setup first |
| M7 | Linking | M6 | Rules/data model first |
| M8 | Remote controls | M7, M2 | Command pipeline first |
| M9 | AI block builder | M4 (wizard step 3), M6-01 (Firebase setup) | Parser ‖ Gemini client |
| M10 | Play readiness | All | Mostly docs/console work |

```
M1 ──┬── M2 ─────────────────────┐
     ├── M3 ── M4 ── M9          ├── M8 ── M10
     └── M5 ── M6 ── M7 ─────────┘
```

---

## 4. Story workflow (for agents and humans)

1. **Pick** a card in the board's `Ready` column (`python3 scripts/backlog/board.py ready`). Ready means every
   "Depends on" issue is closed. Assign yourself and move it: `python3 scripts/backlog/board.py status <N> "In progress"`.
2. **Branch** from `main`: `m<N>/<issue-number>-<short-slug>` (e.g. `m1/27-room-schema`).
3. **Read** the story's *Design reference*: the handoff README section and the prototype (open
   `design_handoff_stay_focused_phase1/Stay Focused Phase 1 Prototype v2.dc.html` in Chrome; the left panel jumps to each flow).
   Prototype logic (dial maths, AI prompt, summary sentences) is in the `<script>` block of that file.
4. **Build** only what the story's *Scope* says. List anything else as a follow-up (a new story file in `backlog/phase1/`).
5. **Verify** locally with the *Definition of done* below, plus the story's acceptance criteria.
6. **Open a PR** titled `[M<N>-<nn>] <story title>` with `Closes #<issue>`, what changed, how it was tested, the acceptance
   criteria ticked, and pending manual checks. Move the card to `In review`.
7. **CodeRabbit reviews the PR** (config: `.coderabbit.yaml`). Fix or answer **every** comment and resolve the threads.
   Never self-approve (`@coderabbitai approve`) or dismiss its review. If CodeRabbit is rate limited, the fallback reviewer
   is **Qodo** (`.pr_agent.toml`; `/agentic_review`), see `docs/agents/PHASE1_EXECUTION.md`.
8. **Merge** (squash) only when the merge gate is met (CI green, `CodeRabbit` check done, 0 unresolved threads, CodeRabbit approved, or the Qodo fallback gate is met, or no new actionable comments on HEAD; see `docs/agents/PHASE1_EXECUTION.md`). The merge closes
   the issue, and the board moves the card to `Done` and promotes newly unblocked cards to `Ready`.

### Definition of done (every story)

- `./gradlew ciCheck` passes locally (before M1-02 lands: `./gradlew assembleDebug`), and every CI job behind `ci-pass` is green
  on the PR. Once M1-14 has landed, the pre-commit hooks must also pass (`pre-commit run --all-files`).
- New logic has unit tests. Pure logic (evaluator, parsers, formatters, aggregation) is test-first.
- UI matches the prototype: colours, type, spacing, radii and copy. Copy is **final**, so don't reword it.
  Strings go in `strings.xml`.
- New UI components have a `@Preview` and, in `:core:ui`, a Roborazzi screenshot test. Coverage stays above the Kover thresholds.
- A story that adds or changes a user journey adds or extends a Maestro flow in `e2e/flows/` (and instrumented tests where useful). Flows under `e2e/flows/smoke/` gate every PR (`e2e-smoke`, kept under ~5 minutes); the rest run in the full suite (`e2e.yml`). See `e2e/flows/README.md`.
- No new permission, manifest flag or Play-relevant declaration unless the story says so.
- Builds without `google-services.json`.
- CI is green, CodeRabbit approved the PR (or Qodo fallback gate met) with no unresolved threads, and every acceptance criterion is ticked in the PR
  (anything needing a real device or a person is listed in `docs/qa/pending-manual-checks.md`).

---

## 5. Conventions

- **Units:** design px → dp 1:1, font px → sp 1:1.
- **Tokens:** never hard-code colours or radii in features; use `StayFocusedTheme.colors / .type / .shapes / .spacing`.
- **State:** each screen has `XxxRoute` (wires the ViewModel) and a stateless `XxxScreen(state, onEvent)` that previews and tests use.
- **Time:** inject `java.time.Clock` (or a `TimeSource` interface) everywhere; never call `System.currentTimeMillis()` in logic.
- **Strings with numbers:** use the formatters in `:core:model` (`formatMinutes`, `dialLabel`, `mmss`, `hms`), ported from the prototype.
- **Logging:** `Timber`. Never log package names of user apps at INFO level or above in release builds.
- **Privacy:** usage data stays on the device, except for a child phone's status/usage sent to the linked parent. AI receives only the typed sentence.

---

## 6. Domain model (source of truth: `:core:model`)

```kotlin
enum class BlockType { LIMIT, CYCLE, SCHEDULE, NOW }
enum class LimitPeriod { DAILY, HOURLY }
sealed interface BlockTarget { data object ThisPhone : BlockTarget; data class Device(val deviceId: String) : BlockTarget }

data class Block(
    val id: String,
    val target: BlockTarget,
    val type: BlockType,
    val name: String,
    val apps: Set<String>,          // package names
    val limitMins: Int?,            // LIMIT
    val period: LimitPeriod?,       // LIMIT
    val useMins: Int?,              // CYCLE
    val restMins: Int?,             // CYCLE
    val range: TimeRange?,          // SCHEDULE, start/end LocalTime, may cross midnight
    val durationMins: Int?,         // NOW
    val startedAt: Instant?,        // NOW
    val days: DaysOfWeek,           // bitmask Mon..Sun, ignored for NOW
    val enabled: Boolean,
    val createdAt: Instant,
    val source: BlockSource,        // MANUAL, TEMPLATE, AI
)
data class BreakSession(val startedAt: Instant, val endsAt: Instant, val lengthMins: Int)
data class Permissions(val usage: Boolean, val accessibility: Boolean, val overlay: Boolean, val notifications: Boolean)
data class LinkedDevice(val id: String, val name: String, val model: String, val battery: Int?, val currentApp: String?,
                        val online: Boolean, val lastSeen: Instant?, val alerts: List<TamperAlert>,
                        val requests: List<UnlockRequest>, val focusEndsAt: Instant?)
```

Dial configs (from the prototype `DIALS`):

| Dial | max | step | presets |
|---|---|---|---|
| Break | 120 | 5 | 15, 30, 60, 120 |
| Limit | 180 | 5 | 15, 30, 60, 90 |
| Cycle: use | 60 | 1 | 5, 10, 15, 30 |
| Cycle: rest | 180 | 5 | 15, 30, 60, 120 |
| Block now | 480 | 15 | 30, 60, 120, 240 |

---

## 7. Firestore data model (M7/M8, final in M7-03)

```
users/{uid}                          { displayName, email, createdAt }                 parent profile
users/{uid}/fcmTokens/{token}        { createdAt, platform: android }                  parent's devices, for push
users/{uid}/devices/{deviceId}       { name, model, linkedAt, childUid, online, lastSeen, battery, charging, currentApp, focusEndsAt }
users/{uid}/devices/{id}/blocks/{blockId}     Block (target = this device)
users/{uid}/devices/{id}/commands/{cmdId}     { type, payload, createdAt, status: pending|done|failed|expired, ackAt? }
users/{uid}/devices/{id}/requests/{reqId}     { app, minutes, status: pending|approved|denied, createdAt, decidedAt? }
users/{uid}/devices/{id}/alerts/{alertId}     { kind: permission_lost|unlinked|offline, permission?, createdAt, dismissed }
users/{uid}/devices/{id}/usage/{yyyy-MM-dd}   { totalMins, apps: [{pkg, label, mins, opens}] }
users/{uid}/devices/{id}/apps/{pkg}           { label }               child's launchable apps
rateLimits/{uid_action}              Functions only, no client access
linkTokens/{token}                   { childUid, model, createdAt, expiresAt (<= now + 5 min), code (6 digits), status: open, claimedBy? }
                                     claimedBy and later statuses (claimed|used|declined) are set by Functions only
```

### Security rules (`firebase/firestore.rules`, tested in `firebase/rules-tests`)

Deny by default. Parent = non-anonymous user with `auth.uid == uid`. Child = user whose uid equals the device's `childUid`.

| Path | Parent | Child |
|---|---|---|
| `users/{uid}` | read; create (`displayName`, `email`, `createdAt` = server time); update `displayName`/`email`; no delete | none |
| `fcmTokens/{token}` | read, write, delete own | none |
| `devices/{id}` | read; **no create/update/delete** (Functions only) | read own; update only `battery`, `charging`, `currentApp`, `online`, `lastSeen` (server time), `focusEndsAt` (null or <= 8 h ahead) |
| `blocks` | read, create, update, delete (whitelisted keys, enums and the fields each block type needs; device must exist) | read |
| `commands` | read; create only (fixed schema, `status: pending`, server `createdAt`, type from the fixed list, payload fixed per type with bounded values) | read; update a `pending` command only: `status` (done/failed/expired) and `ackAt` |
| `requests` | read; decide a pending one (`status` approved/denied, `decidedAt`) | read; create (`app` <= 255, `minutes` 1..480, `pending`) |
| `alerts` | read; update only `dismissed` | read; create (`kind` from the list, `dismissed: false`) |
| `usage/{date}` | read | read; write (`yyyy-MM-dd` id, `totalMins` 0..1440, <= 300 apps) |
| `apps/{pkg}` | read | read; write `label` (<= 100), delete |
| `linkTokens/{token}` | none | create with `childUid == auth.uid`, `expiresAt` in (now, now + 5 min], no `claimedBy`; read own; no update/delete |
| `rateLimits/**`, anything else | none | none |

Every client timestamp (`createdAt`, `decidedAt`, `ackAt`, `lastSeen`) must equal the server time (Block `createdAt`/`startedAt` and `focusEndsAt` are plain timestamps). The list contents of `usage.apps` and
`blocks.apps` are only bounded by size (rules cannot iterate lists); readers must parse defensively.

---

## 8. Backend: Firebase-first, Functions only when needed

**Default:** the apps use Firebase client SDKs directly. Security comes from **Firestore security rules**
(deny by default, field-level validation, tested in CI) and **App Check** (Play Integrity) enforced on Firestore,
the callable Functions and AI Logic.

**A Cloud Function is used only when one of these is true:**
1. It needs credentials that must never ship in an app (sending FCM).
2. It writes across trust boundaries (child data into a parent's tree) and must be atomic.
3. It must finish completely even if the client dies (deleting data).

| Function | Type | Reason | Story |
|---|---|---|---|
| `claimLinkToken` | callable (parent) | 2: atomic claim, rate-limited, can't be replayed | M7-07 |
| `approveLink` | callable (child) | 2: creates the device doc in the parent's tree | M7-07 |
| `unlinkDevice` | callable (parent or child) | 3: complete recursive delete + notify | M7-09 |
| `deleteAccount` | callable (user) | 3: complete deletion (Play requirement) | M6-06 |
| `notifyParent` | Firestore trigger | 1: FCM push for requests, alerts, unlink | M8-05, M8-06 |

**Done without Functions:** parent → child commands (child's foreground-service Firestore listener), child status, usage,
app list, requests and alerts (direct writes guarded by rules), offline detection (derived from `lastSeen` on the parent),
AI (Firebase AI Logic from the app).

**Firestore rules** are the main security boundary for everything done without Functions: see the table in section 7.
Device docs, link-token claiming and deletes are denied to clients and go through Functions only.

**Every Function** uses the shared secure wrapper from M7-02: App Check enforced, auth and role checked in code,
`zod` input validation, per-uid rate limits, its own least-privilege service account, and no personal data in logs.
Deployed only by Terraform (M7-02), never by `firebase deploy`.

---

## 9. Out of scope for Phase 1

Lock/Strict modes, Device Admin, website/Reels blocking, notification blocking, Pomodoro, widgets,
admin/team groups, monetisation. See the phase plan.
