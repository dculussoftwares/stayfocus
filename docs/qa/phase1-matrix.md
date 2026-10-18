# Phase 1 device QA matrix (M10-08)

Full Phase 1 regression on real hardware. Result key: `OK`, `FAIL (issue #n)`, `n/a`, `-` (not run yet).
Every failure is filed as a GitHub issue (label `bug`, milestone `M10 · Play readiness`) and linked in its cell.
The story's exit criterion: **no open blocker issues remain from this matrix.**

## Devices

| Column | Device | OS | Notes |
|---|---|---|---|
| Pixel | Pixel phone | Android 16 (API 36) | Reference device |
| Samsung | Galaxy phone | One UI (latest) | Sleeping apps and "Put unused apps to sleep" |
| Xiaomi | Xiaomi/Redmi/POCO | HyperOS | Autostart, battery saver "No restrictions" |
| Oppo/Vivo | Oppo or Vivo phone | ColorOS / OriginOS | Auto-launch, background freeze |
| API 26 | Phone or emulator | Android 8.0 (minSdk) | |

Record build (`versionName`, commit) and date next to each device when you run it. Each parent/child scenario needs a
second phone with the Kids app (any of the above).

## How to run

1. Install `:app` on the device under test, and `:kids` on a second phone for the linking and remote scenarios.
2. Work top to bottom. For latency rows, use a stopwatch or screen recording; write the median of 5 runs.
3. Battery optimisation "on" = system default for the app; "off" = app set to *Unrestricted / No restrictions* in the
   device's battery settings (follow the in-app guidance from M5-06).
4. Fill in the table and file an issue for each `FAIL`. Attach logcat or a recording.

## Matrix

### Blocking overlay and usage access

| # | Scenario | Expected | Pixel | Samsung | Xiaomi | Oppo/Vivo | API 26 |
|---|---|---|---|---|---|---|---|
| 1 | Grant Usage access and Accessibility from onboarding | Both settings screens open from the app; app detects the grants on return | - | - | - | - | - |
| 2 | Today's total vs Digital Wellbeing | Within 5% | - | - | - | - | - |
| 3 | Block screen on opening a blocked app | Appears within 500 ms; cannot be dismissed into the app with back, recents or home + reopen | - | - | - | - | - |
| 4 | Block screen over a cold-started blocked app from the launcher and from a notification | Same as 3 | - | - | - | - | - |
| 5 | Schedule, usage limit, cycle and break behaviours | Each fires at the configured time/limit; break countdown correct | - | - | - | - | - |
| 6 | Focus session | Blocks the chosen apps for its duration; end screen correct | - | - | - | - | - |
| 7 | Reboot, force-stop, swipe from recents with a block active | See `docs/qa/blocking-matrix.md` rows 1-4 and 12 | - | - | - | - | - |
| 8 | Block screen text in a second device language | Strings localised, layout intact | - | - | - | - | - |

### Remote command latency (parent to child)

Measure from tapping the action on the parent phone to the effect on the Kids phone (block applied / app locked).
Child on mobile data or Wi-Fi, screen on, then screen off for 2 min.

| # | Scenario | Expected | Pixel | Samsung | Xiaomi | Oppo/Vivo | API 26 |
|---|---|---|---|---|---|---|---|
| 9 | Remote block, battery optimisation **on**, screen on | Median < 5 s | - | - | - | - | - |
| 10 | Remote block, battery optimisation **on**, screen off 2 min | Record median; note any delay > 60 s | - | - | - | - | - |
| 11 | Remote block, battery optimisation **off**, screen on | Median < 5 s | - | - | - | - | - |
| 12 | Remote block, battery optimisation **off**, screen off 2 min | Median < 10 s | - | - | - | - | - |
| 13 | Remote lock/unlock and focus start | Same latency as 9-12 | - | - | - | - | - |
| 14 | Unlock request from child | Parent push arrives < 10 s with battery optimisation off | - | - | - | - | - |
| 15 | Tamper alert: Accessibility turned off on child | Parent push and Remote card within 2 min | - | - | - | - | - |

### Linking and accounts

| # | Scenario | Expected | Pixel | Samsung | Xiaomi | Oppo/Vivo | API 26 |
|---|---|---|---|---|---|---|---|
| 16 | Link Kids phone with code | Both sides show linked; child appears in parent's list | - | - | - | - | - |
| 17 | Unlink from parent, then re-link | Clean state both sides | - | - | - | - | - |
| 18 | Sign in, sign out, delete account | Data cleared; app returns to onboarding | - | - | - | - | - |

### AI describe

| # | Scenario | Expected | Pixel | Samsung | Xiaomi | Oppo/Vivo | API 26 |
|---|---|---|---|---|---|---|---|
| 19 | Describe a rule, review AI DRAFT, accept | Draft shown for review, nothing saved before accept | - | - | - | - | - |
| 20 | Offline and rate-limit behaviour | Friendly error, no crash | - | - | - | - | - |

## Failures

| Issue | Row | Device | Severity (blocker / major / minor) | Status |
|---|---|---|---|---|
| - | - | - | - | - |

## Sign-off

- [ ] All five devices run, all rows filled in
- [ ] Every `FAIL` has an issue
- [ ] No open blocker issues
