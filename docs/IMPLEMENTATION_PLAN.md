# Stay Focused: Phase 1 implementation plan

This is the engineering plan for Phase 1 (v1.0). Product scope, screens and design tokens live in
[`design_handoff_stay_focused_phase1/README.md`](../design_handoff_stay_focused_phase1/README.md). Read that first.

The work is tracked as **10 epics (one per milestone) with stories as sub-issues** on the
GitHub Project board for `dculussoftwares/stayfocus`. Every story is meant to be picked up and finished by
one coding agent or developer in one PR. This document is the shared contract all stories refer to.

---

## 1. Decisions (agreed)

| Topic | Decision |
|---|---|
| Platform | Android only, Kotlin + Jetpack Compose, Material 3 as base, fully custom-themed (dark only) |
| SDK levels | `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36` |
| DI | Hilt (KSP) |
| Persistence | Room (schema exported to `/schemas`) + Preferences DataStore |
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
| Tests | JUnit4 + Turbine + MockK for unit tests; Compose UI tests; Roborazzi screenshot tests for `:core:ui`; Firestore emulator tests for rules/functions |
| Lint | Spotless (ktlint) + detekt + Android Lint, all run in CI |

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

1. **Pick** a story on the board in the `Ready` column whose "Depends on" issues are all closed.
   Assign yourself and move it to `In progress`.
2. **Branch** from `main`: `m<N>/<issue-number>-<short-slug>` (e.g. `m1/12-room-schema`).
3. **Read** the story's *Design reference*: the handoff README section and the prototype (open
   `design_handoff_stay_focused_phase1/Stay Focused Phase 1 Prototype v2.dc.html` in Chrome; the left panel jumps to each flow).
   Prototype logic (dial maths, AI prompt, summary sentences) is in the `<script>` block of that file.
4. **Build** only what the story's *Scope* says. Anything else goes in a new issue, not in this PR.
5. **Verify** with the *Definition of done* below, plus the story's acceptance criteria.
6. **PR** titled `[M<N>] <story title> (#<issue>)` with `Closes #<issue>` in the body. Fill in the PR template:
   what changed, how it was tested, screenshots for UI.
7. Move the card to `In review`. It moves to `Done` on merge.

### Definition of done (every story)

- `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug` passes locally and in CI.
- New logic has unit tests. Pure logic (evaluator, parsers, formatters, aggregation) is test-first.
- UI matches the prototype: colours, type, spacing, radii and copy. Copy is **final**, so don't reword it.
  Strings go in `strings.xml`.
- New UI components have a `@Preview` and, in `:core:ui`, a Roborazzi screenshot test.
- No new permission, manifest flag or Play-relevant declaration unless the story says so.
- Builds without `google-services.json`.
- The story's acceptance-criteria checkboxes are all ticked in the PR description.

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

## 7. Firestore data model (M7/M8, refined in M7-03)

```
users/{uid}                          { displayName, email, createdAt }
users/{uid}/fcmTokens/{token}        { createdAt, platform }        parent's devices, for push
users/{uid}/devices/{deviceId}       { name, model, linkedAt, childUid, online, lastSeen, battery, charging, currentApp, focusEndsAt }
users/{uid}/devices/{id}/blocks/{blockId}     Block (target = this device)
users/{uid}/devices/{id}/commands/{cmdId}     { type, payload, createdAt, status: pending|done|failed|expired, ackAt }
users/{uid}/devices/{id}/requests/{reqId}     { app, minutes, status: pending|approved|denied, createdAt, decidedAt }
users/{uid}/devices/{id}/alerts/{alertId}     { kind: permission_lost|unlinked|offline, permission?, createdAt, dismissed }
users/{uid}/devices/{id}/usage/{yyyy-MM-dd}   { totalMins, apps: [{pkg, label, mins, opens}] }
users/{uid}/devices/{id}/apps/{pkg}           { label }               child's launchable apps
rateLimits/{uid_action}              Functions only, no client access
linkTokens/{token}                   { childUid, model, createdAt, expiresAt (+5 min), claimedBy?, code (6 digits), status: open|claimed|used|declined }
```

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

**Every Function** uses the shared secure wrapper from M7-02: App Check enforced, auth and role checked in code,
`zod` input validation, per-uid rate limits, its own least-privilege service account, and no personal data in logs.
Deployed only by Terraform (M7-02), never by `firebase deploy`.

---

## 9. Out of scope for Phase 1

Lock/Strict modes, Device Admin, website/Reels blocking, notification blocking, Pomodoro, widgets,
admin/team groups, monetisation. See the phase plan.
